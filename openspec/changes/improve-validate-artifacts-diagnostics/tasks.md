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
- [ ] 2.3 Broaden `find()`'s catch from `HttpResponseException` to `Exception`; 404 stays `null`,
  every other outcome throws `ArtifactStoreUnavailableException` with the message from design.md §3.
- [ ] 2.4 Write unit tests (new `StorageServiceImplTest`, server module) before/alongside 2.1–2.3:
  - Not-found message contains the artifact path, the repositories checked, and the "verify
    published / check coordinates" guidance.
  - A non-404 `HttpResponseException` from the mocked `Artifactory` chain during lookup produces
    `ArtifactStoreUnavailableException` with the repository and root-cause text.
  - A non-HTTP exception (e.g. a plain `IOException`/`RuntimeException`) during lookup also
    produces `ArtifactStoreUnavailableException`, not an uncaught propagation.
  - A confirmed 404 from every repository still throws `UnableToFindArtifactException` (not the
    new exception) — regression guard so the two failure modes stay distinct.
- [ ] 2.5 Confirm `:common:test :server:test` pass.

## 3. Client-side aggregation (`client/maven-dms-plugin`)

- [ ] 3.1 Add test dependencies to `client/maven-dms-plugin/build.gradle.kts` (junit-bom,
  junit-jupiter-api, junit-jupiter-params), mirroring `test-common/build.gradle.kts:5-7`.
- [ ] 3.2 Write failing unit test(s) for `ArtifactServiceImpl.processArtifacts` (new
  `ArtifactServiceImplTest`, first test in this module) using a hand-written fake `Log` and a
  `processFunction` that throws for some artifacts:
  - The thrown `MojoFailureException`'s message contains every failed artifact's own exception
    message and a summary count.
  - The fake `Log`'s captured ERROR-level calls are message-only (no `Throwable` argument); the
    full exception is only passed to the DEBUG-level call.
- [ ] 3.3 Implement: replace `exceptions.forEach(log::error)` / count-only
  `MojoFailureException` per design.md §5.
- [ ] 3.4 Confirm `:client:maven-dms-plugin:test` passes.

## 4. FT assertion + finalization

- [ ] 4.1 Extend `test-common/.../DmsServiceApplicationBaseTest.kt`'s `testAddInvalidArtifacts`
  (`:199-205`, using the existing `invalidArtifacts()` source at `:1222`) to assert on the new
  message content (contains the artifact path and the "verify published / check coordinates"
  text), not just the exception type.
- [ ] 4.2 Run `:ft:test` on CI (per this repo's convention — infra-dependent, not run locally) and
  confirm it's green.
- [ ] 4.3 Manually re-read the new message strings against the original OCTOPUS-2419 log and
  confirm they answer "why" for a component owner.
- [ ] 4.4 Mark this openspec change's requirements as implemented; no further doc updates expected
  unless implementation surfaces a gap the docs need to reflect.
