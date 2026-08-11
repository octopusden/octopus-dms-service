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
  - Caller-neutral wording: `get()` also serves `download()` and `registerArtifact`'s checksum
    re-check, so the message names neither a validation step nor validation-supplied coordinates.
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
- [x] 2.5 Write unit tests (new `StorageServiceImplTest`, server module) before/alongside 2.1–2.4.
  Properties configure one repository of every kind (upload/staging/release/cold) so the walk itself
  is exercised, and the `Artifactory` mock pins an outcome per repository so order can be asserted:
  - **Walk** — a hit in the first repository returns without querying the later ones; a 404 in an
    earlier repository falls through to a later hit; `includeStaging = false` skips staging; a Docker
    lookup queries `$path/manifest.json`.
  - **Not found** — every repository 404s: `find()` returns `null`, `get()` throws
    `UnableToFindArtifactException` (code `DMS-40006`) naming the path, *every* repository checked,
    and the "verify published / check coordinates" guidance.
  - **Caller-neutral message** — the not-found message contains no mention of validation (2.2).
  - **Abort, don't fall through** — a non-404 status throws `ArtifactStoreUnavailableException`
    (code `DMS-40015`) with the repository, path and root-cause text, and the next repository is
    never queried.
  - **Credentials vs. transient** — 401/403/407 produce the credentials wording, not the unqueryable
    wording; parameterized over the three statuses.
  - **Connection failure** — a plain `IOException` also produces `ArtifactStoreUnavailableException`
    rather than propagating raw, and is retained as the thrown exception's `cause`.
  - **Programming error** — a `NullPointerException` propagates unchanged; regression guard proving
    it isn't mislabeled as "Artifactory unavailable".
- [x] 2.6 Confirm `:common:test` and the new server test pass.
  - `:common:test` has no source.
  - `StorageServiceImplTest` — 15/15 green.
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
  `validate-artifacts` tests. Run the goal against one Maven and one Debian coordinate that don't
  exist, and assert:
  - The DMS exception's class name is absent from the output — no per-artifact throwable dump.
    Deliberately not a `"\tat "` check: `runMavenDmsPlugin` passes `-e`, so Maven prints one stack
    trace for its own exception pair regardless (design.md §6).
  - Each artifact's identifier appears on the same line as the actionable not-found text, via
    `assertActionableNotFound`. Asserting the message once anywhere in the output would pass while
    one artifact stayed generic.
  - The `"N of M artifact(s) failed"` summary is present.

## 5. Outstanding

- [ ] 5.1 Run `:ft:test` and confirm it's green, including 4.2. Needs the module's live
  Postgres/Artifactory/component-registry stack — not runnable in this environment.
- [ ] 5.2 Run the full `:dms-service:test` and confirm `StorageServiceImplTest` plus the Spring
  context test are green. Needs the module's OKD-provisioned Postgres.

Everything in §1–§4 is implemented and locally verified as far as this environment allows; §5 is the
only remaining verification.
