## Why

The `maven-dms-plugin:validate-artifacts` goal fails with diagnostics that are technically
accurate but not actionable for the component owner running it. When artifacts fail validation,
each one produces the same generic message —

> `Artifact <path> not found in repositories [<repo-1>, <repo-2>, ...]`

— wrapped in a full Java stack trace, repeated once per failed artifact. The build itself then
fails with `"N exception(s) occurred"`, which is the only text a caller sees without opening the
full log. To learn *anything* about which artifacts failed or why, the log has to be read in full
past however many stack traces there are.

Three distinct gaps combine to produce this:

1. **The root message doesn't say what to check.** `StorageServiceImpl.get()` throws
   `UnableToFindArtifactException` with only the path and the list of repositories searched — no
   suggestion that the artifact might never have been published, or that its coordinates/version
   might not match what was actually published.
2. **A different failure mode is silently misrepresented as "missing."** `StorageServiceImpl.find()`
   treats a 404 from Artifactory as "not found" (correct), but any *other* Artifactory failure
   while checking a repository — auth, network, 5xx — is rethrown unchanged, bypasses DMS's own
   exception hierarchy entirely, and surfaces as an uncoded HTTP 500. There is no way for a reader
   of the log to tell "Artifactory couldn't be reached" apart from "the artifact really isn't
   there," even though the fix for each is completely different.
3. **The client discards what little signal exists.** `ArtifactServiceImpl.processArtifacts()` (in
   `client/maven-dms-plugin`) logs every collected failure via `log::error(Throwable)` — dumping a
   full stack trace per artifact — and then fails the build with a bare `"N exception(s)
   occurred"`, which is the only summary text the caller actually sees.

## What Changes

- **Actionable not-found message.** `UnableToFindArtifactException`'s message names the artifact,
  the repositories checked, and suggests what to verify (was it published before this step ran?
  do the coordinates/version match what was published?).
- **A distinct "couldn't check" failure.** A new `ArtifactStoreUnavailableException` (DMS-40015,
  HTTP 503) is thrown when a repository lookup itself fails (non-404 HTTP error, or a connection
  failure) — so this stops being misread as "artifact missing" and stops being an uncoded 500.
- **A clean, informative build failure.** `maven-dms-plugin`'s `ArtifactServiceImpl` no longer
  dumps a stack trace per failed artifact at ERROR level; each failure logs its message once, full
  traces move to DEBUG, and the final `MojoFailureException` message is composed from each
  artifact's own message — so that text, not a bare count, is the summary a caller actually sees.

## Affected areas

- `common` — new `ArtifactStoreUnavailableException` in `ServicesExceptions.kt`.
- `server` — `StorageServiceImpl` (message content, error wrapping, constructor seam for
  testability), `ExceptionHandler` (new mapping to 503).
- `client/maven-dms-plugin` — `ArtifactServiceImpl.processArtifacts()`'s failure aggregation.
- `test-common` — the existing FT assertion `testAddInvalidArtifacts` (`DmsServiceApplicationBaseTest.kt`)
  is extended to assert on message content, not just exception type.
- `ft` — a new FT test (`DmsServiceApplicationFunctionalTest.kt`) runs the real
  `validate-artifacts` Maven goal against a nonexistent artifact and asserts on the actual console
  output (no stack traces, actionable message, composed summary) — this is where the client-side
  aggregation change (§ above) is verified.
- No change to `DmsClientErrorDecoder` — it already reconstructs exceptions by error code alone,
  independent of HTTP status, so the new exception type needs no client-side wiring beyond being
  registered in `DMSException.CODE_EXCEPTION_MAP`.

## Out of scope

- **Distinguishing "never published" from "wrong coordinates/version."** Both produce an identical
  signal — 404 from every configured repository — so DMS has no data available to tell them apart.
  This change makes the single resulting message more actionable instead of fabricating a
  distinction the underlying system can't actually make.
- **RPM/DEB/Docker-specific wording.** All artifact types share `StorageServiceImpl.get()`/`find()`;
  the new message is generic enough to apply to all of them (the `path` already encodes the
  type-specific structure), so no per-type message variants are introduced.
- **`gradle-dms-plugin`.** It is a separate client with its own call path and is not touched here.
  If the same aggregation problem exists there, that is a follow-up, not part of this change.
