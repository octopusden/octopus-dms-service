## Why

`maven-dms-plugin:validate-artifacts` fails with diagnostics that are technically accurate but not
actionable for the component owner running it. Today a failed run gives:

- One generic message per failed artifact: `Artifact <path> not found in repositories [<repo-1>, ...]`
- Each one wrapped in a full Java stack trace, repeated once per failed artifact
- A build failure whose entire message is `"N exception(s) occurred"`

To learn *which* artifacts failed or *why*, the whole log has to be read past however many stack
traces there are.

Three separate gaps combine to produce that:

1. **The root message doesn't say what to check.** `StorageServiceImpl.get()` throws
   `UnableToFindArtifactException` with only the path and the repositories searched. It never
   suggests the artifact may not have been published yet, or that its coordinates/version may not
   match what was published.
2. **A different failure mode is misreported as "missing."** `find()` correctly reads a 404 as "not
   found", but any *other* Artifactory failure — auth, network, 5xx — is rethrown unchanged. It
   bypasses DMS's exception hierarchy and surfaces as an uncoded HTTP 500. Nothing in the log
   separates "Artifactory couldn't be reached" from "the artifact really isn't there", even though
   the two need completely different fixes.
3. **The client discards what signal exists.** `ArtifactServiceImpl.processArtifacts()` logs every
   collected failure through `log::error(Throwable)` — a full stack trace each — then fails with a
   bare count.

## What Changes

**Actionable not-found message**
- `UnableToFindArtifactException` names the artifact and the repositories checked
- It also says what to verify: was it published before this step ran, do the coordinates/version
  match what was published

**A distinct "couldn't check" failure**
- New `ArtifactStoreUnavailableException` — `DMS-40015`, HTTP 503
- Thrown when the lookup itself fails: non-404 HTTP status, or a connection/timeout/DNS error
- Stops being misread as "artifact missing"; stops being an uncoded 500
- Its message splits two cases: a failure that may clear on its own, versus a rejected-credentials
  status (401/403/407) that is a DMS configuration problem retrying will never fix

**The wrapped failure keeps its cause**
- `DMSException` takes an optional trailing `cause`
- The `IOException` being replaced still reaches the server log with its stack trace intact
- Nothing changes on the wire — a client still gets only the code and one sentence

**A clean, informative build failure**
- No more stack trace per failed artifact at ERROR level; full traces move to DEBUG
- Each failure logs its own message once
- `MojoFailureException`'s message is composed from those messages, not a bare count

## Affected areas

| Module | Change |
|---|---|
| `common` | New `ArtifactStoreUnavailableException` in `ServicesExceptions.kt`; optional trailing `cause` on the `DMSException` base class (defaulted, so no subclass changes) |
| `server` | `StorageServiceImpl` — message content, error wrapping, constructor seam for testability; `ExceptionHandler` — new mapping to 503 |
| `client/maven-dms-plugin` | `ArtifactServiceImpl.processArtifacts()`'s failure aggregation |
| `test-common` | `testAddInvalidArtifacts` (`DmsServiceApplicationBaseTest.kt`) now asserts on message content, not just exception type |
| `ft` | New `testMavenDmsPluginValidateArtifactsArtifactNotFound` runs the real Maven goal against a nonexistent artifact and asserts on real console output — this is where the client-side aggregation is verified |

No change to `DmsClientErrorDecoder`: it reconstructs exceptions by error code alone, independent of
HTTP status, so the new exception needs no client wiring beyond its `CODE_EXCEPTION_MAP` entry.

## Out of scope

- **Distinguishing "never published" from "wrong coordinates/version."** Both produce an identical
  signal — 404 from every configured repository — so DMS has no data to tell them apart. This change
  makes the one resulting message more actionable rather than fabricating a distinction the
  underlying system can't make.
- **RPM/DEB/Docker-specific wording.** All types share `get()`/`find()`, and `path` already encodes
  the type-specific structure, so one generic message covers all of them.
- **Retry logic.** Making an Artifactory outage legible is not the same as surviving it; `find()`
  still fails on the first bad attempt.
- **`gradle-dms-plugin`.** A separate client with its own call path, untouched here. If the same
  aggregation problem exists there it's a follow-up.
