## 1. Exception type + server mapping

- [x] 1.1 Add `ArtifactStoreUnavailableException(message, cause) : DMSException(message, "DMS-40015",
  cause)` to `common/.../exception/ServicesExceptions.kt`.
  - Register it in `CODE_EXCEPTION_MAP`, which passes message only — client-side reconstructions
    have no cause.
- [x] 1.2 Add an `@ExceptionHandler(ArtifactStoreUnavailableException::class)` method in
  `server/.../controller/advice/ExceptionHandler.kt` → `HttpStatus.SERVICE_UNAVAILABLE`.
  - Its own method, not folded into the existing `BAD_REQUEST` group (design.md §1).
- [x] 1.3 Give `DMSException` a trailing `cause: Throwable? = null`, passed to `RuntimeException`
  (design.md §7).
  - Last and defaulted, so every existing subclass is untouched.
  - Wire format unchanged — `ApplicationErrorResponse` still carries only `code`/`detail`/`message`.

## 2. `StorageServiceImpl` — messages, error wrapping, testability seam

- [x] 2.1 Add `Artifactory` as a constructor parameter, defaulted to today's builder expression, so a
  mock can be substituted in tests.
- [x] 2.2 Extract the not-found message into a private function; update its text per design.md §3.
- [x] 2.3 Add `catch (e: IOException)` to `find()`, after the existing `HttpResponseException` (404)
  branch.
  - Covers connection/timeout/DNS too, since `HttpResponseException` is itself an `IOException`.
  - Every outcome caught here throws `ArtifactStoreUnavailableException`.
  - Deliberately not `catch (e: Exception)` — that would swallow a programming error (an NPE, say)
    into a misleading "Artifactory unavailable" message instead of letting it surface as a bug.
- [x] 2.4 Close `storeUnavailable()`'s message on a status-dependent sentence per design.md §3.
  - Status in `CREDENTIAL_REJECTION_STATUSES` (401/403/407) → a DMS configuration problem retrying
    will not fix.
  - Anything else → Artifactory being unqueryable.
  - Pass the caught `IOException` as the exception's cause (1.3).
- [x] 2.5 Write unit tests (new `StorageServiceImplTest`, server module) before/alongside 2.1–2.4:
  - Not-found message contains the artifact path, the repositories checked, and the "verify
    published / check coordinates" guidance.
  - A non-404 `HttpResponseException` produces `ArtifactStoreUnavailableException` with the
    repository and root-cause text.
  - A 401/403/407 `HttpResponseException` produces the credentials wording, not the unqueryable
    wording — parameterized over the three statuses.
  - A plain `IOException` (connection failure) also produces `ArtifactStoreUnavailableException`,
    not an uncaught propagation.
  - That `IOException` is retained as the thrown exception's `cause`.
  - A confirmed 404 from every repository still throws `UnableToFindArtifactException` — regression
    guard keeping the two failure modes distinct.
  - A non-`IOException` programming error (`NullPointerException`) propagates unchanged — regression
    guard proving it isn't mislabeled as "Artifactory unavailable".
- [x] 2.6 Confirm `:common:test` and the new server test pass.
  - `:common:test` has no source.
  - `StorageServiceImplTest` — 8/8 green.
  - It can't run under the real `:dms-service:test` task locally, which is gated on the module's
    OKD-provisioned Artifactory/Postgres. Verified by bypassing that gate, since the test itself
    touches none of that infra.

## 3. Client-side aggregation (`client/maven-dms-plugin`)

- [x] 3.1 Replace `exceptions.forEach(log::error)` and the count-only `MojoFailureException` in
  `ArtifactServiceImpl.processArtifacts`, per design.md §5.
  - Message per failure at ERROR, stack trace at DEBUG.
  - Summary composed from each failure's message.
  - Summary text is goal-agnostic — `processArtifacts` also backs `upload-artifacts`.
- [x] 3.2 Confirm `:maven-dms-plugin:test` still passes.
  - No dedicated unit test added; verified at FT level per design.md §6.

## 4. FT assertions

- [x] 4.1 Extend `test-common/.../DmsServiceApplicationBaseTest.kt`'s `testAddInvalidArtifacts` (which
  uses the existing `invalidArtifacts()` source) to assert on message content — the artifact path and
  the "verify published / check coordinates" text — not just the exception type.
- [x] 4.2 Add `testMavenDmsPluginValidateArtifactsArtifactNotFound` to
  `ft/src/ft/kotlin/.../DmsServiceApplicationFunctionalTest.kt`, alongside the existing
  `validate-artifacts` tests. Run the goal against coordinates that don't exist and assert:
  - The DMS exception's class name is absent from the output — no per-artifact throwable dump.
    Deliberately not a `"\tat "` check: `runMavenDmsPlugin` passes `-e`, so Maven prints one stack
    trace for its own exception pair regardless (design.md §6).
  - The actionable not-found text is present.
  - The `"N of M artifact(s) failed"` summary is present.

## 5. Outstanding

- [ ] 5.1 Run `:ft:test` and confirm it's green, including 4.2. Needs the module's live
  Postgres/Artifactory/component-registry stack — not runnable in this environment.
- [ ] 5.2 Run the full `:dms-service:test` and confirm `StorageServiceImplTest` plus the Spring
  context test are green. Needs the module's OKD-provisioned Postgres.

Everything in §1–§4 is implemented and locally verified as far as this environment allows; §5 is the
only remaining verification.
