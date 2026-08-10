## 1. Exception type + server mapping

- [ ] 1.1 Add `ArtifactStoreUnavailableException(message) : DMSException(message, "DMS-40015")` to
  `common/src/main/kotlin/org/octopusden/octopus/dms/exception/ServicesExceptions.kt`, registered
  in `CODE_EXCEPTION_MAP`.
- [ ] 1.2 Add an `@ExceptionHandler(ArtifactStoreUnavailableException::class)` method in
  `server/.../controller/advice/ExceptionHandler.kt` → `HttpStatus.SERVICE_UNAVAILABLE`.

## 2. `StorageServiceImpl` — messages, error wrapping, testability seam

- [ ] 2.1 Add `Artifactory` as a constructor parameter (default = today's builder expression) so
  it can be substituted with a mock in tests.
- [ ] 2.2 Extract the not-found message into a small private function; update its text per
  design.md §3.
- [ ] 2.3 Add a second catch on `find()`, `catch (e: IOException)` after the existing
  `HttpResponseException` (404) branch — covers connection/timeout/DNS failures too, since
  `HttpResponseException` is itself an `IOException` subtype. Every outcome caught here throws
  `ArtifactStoreUnavailableException` with the message from design.md §3. Deliberately not
  `catch (e: Exception)` — that would also swallow a programming error (e.g. an NPE) into a
  misleading "Artifactory unavailable" message instead of letting it surface as a bug.
- [ ] 2.4 Write unit tests (new `StorageServiceImplTest`, server module) before/alongside 2.1–2.3:
  - Not-found message contains the artifact path, the repositories checked, and the "verify
    published / check coordinates" guidance.
  - A non-404 `HttpResponseException` from the mocked `Artifactory` chain during lookup produces
    `ArtifactStoreUnavailableException` with the repository and root-cause text.
  - A plain `IOException` (connection failure) during lookup also produces
    `ArtifactStoreUnavailableException`, not an uncaught propagation.
  - A confirmed 404 from every repository still throws `UnableToFindArtifactException` (not the
    new exception) — regression guard so the two failure modes stay distinct.
  - A non-`IOException` programming error (e.g. `NullPointerException`) during lookup propagates
    unchanged — regression guard proving it is not mislabeled as "Artifactory unavailable".
- [ ] 2.5 Confirm `:common:test :server:test` pass.

## 3. Client-side aggregation (`client/maven-dms-plugin`)

- [ ] 3.1 Implement: replace `exceptions.forEach(log::error)` / count-only
  `MojoFailureException` per design.md §5, in `ArtifactServiceImpl.processArtifacts`.
- [ ] 3.2 Confirm `:maven-dms-plugin:compileJava`/`:maven-dms-plugin:test` still pass (no
  dedicated unit test added for this — see design.md §6; verified at the FT level, §4 below).

## 4. FT assertion + finalization

- [ ] 4.1 Extend `test-common/.../DmsServiceApplicationBaseTest.kt`'s `testAddInvalidArtifacts`
  (`:199-205`, using the existing `invalidArtifacts()` source at `:1222`) to assert on the new
  message content (contains the artifact path and the "verify published / check coordinates"
  text), not just the exception type.
- [ ] 4.2 Add `testMavenDmsPluginValidateArtifactsArtifactNotFound` to
  `ft/src/ft/kotlin/.../DmsServiceApplicationFunctionalTest.kt`, alongside the existing
  `validate-artifacts` tests: run the goal against coordinates that don't exist, assert no
  stack-trace lines in the output, the actionable not-found text is present, and the composed
  `"N of M artifact(s) failed validation"` summary is present (design.md §6).
- [ ] 4.3 Run `:ft:test` on CI (per this repo's convention — infra-dependent, not run locally) and
  confirm it's green, including 4.2's new test.
- [ ] 4.4 Manually re-read the new message strings against the original OCTOPUS-2419 log and
  confirm they answer "why" for a component owner.
- [ ] 4.5 Mark this openspec change's requirements as implemented; no further doc updates expected
  unless implementation surfaces a gap the docs need to reflect.
