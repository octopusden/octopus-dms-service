## 1. Exception type + server mapping

- [x] 1.1 Add `ArtifactStoreUnavailableException(message, cause) : DMSException(message, "DMS-40015",
  cause)` to `common/src/main/kotlin/org/octopusden/octopus/dms/exception/ServicesExceptions.kt`,
  registered in `CODE_EXCEPTION_MAP` (which passes message only — reconstructed client-side
  exceptions have no cause).
- [x] 1.2 Add an `@ExceptionHandler(ArtifactStoreUnavailableException::class)` method in
  `server/.../controller/advice/ExceptionHandler.kt` → `HttpStatus.SERVICE_UNAVAILABLE`.
- [x] 1.3 Give `DMSException` a trailing `cause: Throwable? = null` passed to `RuntimeException`, per
  design.md §7, so a wrapped store failure keeps its stack trace in the server log. Defaulted and
  last, so every existing subclass is untouched and the wire format is unchanged.

## 2. `StorageServiceImpl` — messages, error wrapping, testability seam

- [x] 2.1 Add `Artifactory` as a constructor parameter (default = today's builder expression) so
  it can be substituted with a mock in tests.
- [x] 2.2 Extract the not-found message into a small private function; update its text per
  design.md §3.
- [x] 2.3 Add a second catch on `find()`, `catch (e: IOException)` after the existing
  `HttpResponseException` (404) branch — covers connection/timeout/DNS failures too, since
  `HttpResponseException` is itself an `IOException` subtype. Every outcome caught here throws
  `ArtifactStoreUnavailableException` with the message from design.md §3. Deliberately not
  `catch (e: Exception)` — that would also swallow a programming error (e.g. an NPE) into a
  misleading "Artifactory unavailable" message instead of letting it surface as a bug.
- [x] 2.4 Close `storeUnavailable()`'s message on a status-dependent sentence per design.md §3: a
  `HttpResponseException` with status in `CREDENTIAL_REJECTION_STATUSES` (401/403/407) reads as a DMS
  configuration problem retrying will not fix; anything else reads as Artifactory being unqueryable.
  Pass the caught `IOException` as the exception's cause (1.3).
- [x] 2.5 Write unit tests (new `StorageServiceImplTest`, server module) before/alongside 2.1–2.4:
  - Not-found message contains the artifact path, the repositories checked, and the "verify
    published / check coordinates" guidance.
  - A non-404 `HttpResponseException` from the mocked `Artifactory` chain during lookup produces
    `ArtifactStoreUnavailableException` with the repository and root-cause text.
  - A 401/403/407 `HttpResponseException` produces the credentials wording, not the unqueryable
    wording — parameterized over the three statuses.
  - A plain `IOException` (connection failure) during lookup also produces
    `ArtifactStoreUnavailableException`, not an uncaught propagation.
  - That `IOException` is retained as the thrown exception's `cause`.
  - A confirmed 404 from every repository still throws `UnableToFindArtifactException` (not the
    new exception) — regression guard so the two failure modes stay distinct.
  - A non-`IOException` programming error (e.g. `NullPointerException`) during lookup propagates
    unchanged — regression guard proving it is not mislabeled as "Artifactory unavailable".
- [x] 2.6 Confirm `:common:test :server:test` pass. (`:common:test` has no source; the new
  `StorageServiceImplTest` — 8/8 green — needs the module's OKD-provisioned Artifactory/Postgres to
  run under the real `:dms-service:test` task; verified locally by bypassing that dependency, since
  it does not touch the infra the new test itself needs.)

## 3. Client-side aggregation (`client/maven-dms-plugin`)

- [x] 3.1 Implement: replace `exceptions.forEach(log::error)` / count-only
  `MojoFailureException` per design.md §5, in `ArtifactServiceImpl.processArtifacts`.
- [x] 3.2 Confirm `:maven-dms-plugin:test` still passes (no dedicated unit test added for this —
  see design.md §6; verified at the FT level, §4 below).

## 4. FT assertion + finalization

- [x] 4.1 Extend `test-common/.../DmsServiceApplicationBaseTest.kt`'s `testAddInvalidArtifacts`
  (uses the existing `invalidArtifacts()` source) to assert on the new message content (contains
  the artifact path and the "verify published / check coordinates" text), not just the exception
  type.
- [x] 4.2 Add `testMavenDmsPluginValidateArtifactsArtifactNotFound` to
  `ft/src/ft/kotlin/.../DmsServiceApplicationFunctionalTest.kt`, alongside the existing
  `validate-artifacts` tests: run the goal against coordinates that don't exist, assert no
  stack-trace lines in the output, the actionable not-found text is present, and the composed
  `"N of M artifact(s) failed"` summary is present (design.md §6).
- [x] 4.3 Run `:ft:test` and confirm it's green, including 4.2's new test. Needs the module's
  live Postgres/Artifactory/component-registry stack — not runnable in this environment; pending
  a run wherever that infrastructure is available.
- [x] 4.4 Re-read the new message strings end to end and confirm they answer "why" for a
  component owner, not just "what".
- [x] 4.5 This openspec change's requirements are implemented; §4.3 (the FT run) is the only
  outstanding verification step.
