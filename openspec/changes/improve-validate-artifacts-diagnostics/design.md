## Context

`StorageServiceImpl` (server) is the only place that talks to Artifactory when validating/adding
an artifact. `get()` calls `find()`, which walks the configured repositories for a
`RepositoryType` (upload/staging/release/cold) and returns the first `File` info found, or `null`
if every repository 404s. `get()` turns a `null` into `UnableToFindArtifactException`. This
exception (like every `DMSException` subclass) crosses the server→client boundary as a JSON
`{code, message}` pair (`ApplicationErrorResponse`); the client (`DmsClientErrorDecoder`)
reconstructs the exception from `DMSException.CODE_EXCEPTION_MAP` by `code` alone, and the
`maven-dms-plugin` mojo layer catches it generically as an `Exception` at every call site.

## Decisions

### 1. New exception: `ArtifactStoreUnavailableException` (DMS-40015 → HTTP 503)

Added to `common/.../exception/ServicesExceptions.kt`, same shape as every existing
`DMSException` subclass (message-only, no `cause` field — consistent with the rest of the
hierarchy, since the cause can't cross the REST boundary anyway; the root cause's own message is
folded into the exception's message text instead).

Code `DMS-40015` — the next free code (`DMS-40005`, `-40009`, `-40010` are historical gaps in
`CODE_EXCEPTION_MAP`/the class list and are not reused).

Mapped in `ExceptionHandler.kt` via its own `@ExceptionHandler(ArtifactStoreUnavailableException::class)`
method → `HttpStatus.SERVICE_UNAVAILABLE`, **not** folded into the existing `BAD_REQUEST` group.
The existing group represents "the request was well-formed but factually wrong" (bad coordinates,
already exists, etc.) — this new exception means "DMS couldn't even determine an answer because
its dependency (Artifactory) failed," a different class of problem a monitoring dashboard or an
automated retry would want to treat differently from a 400.

No client-side wiring is needed beyond registering the new exception in
`DMSException.CODE_EXCEPTION_MAP`: `DmsClientErrorDecoder.decode` (`client/client/.../DmsClientErrorDecoder.kt:24-26`)
reconstructs by `code` from the JSON body, independent of the HTTP status; and every mojo call site
that talks to DMS (`DMSServiceImpl.java:60`, `ArtifactServiceImpl.java:197` in
`client/maven-dms-plugin`) already catches `Exception` generically.

### 2. `StorageServiceImpl.find()` distinguishes "confirmed absent" from "couldn't check"

Today:
```kotlin
} catch (e: HttpResponseException) {
    if (e.statusCode == 404) null else throw e
}
```
Any non-404 `HttpResponseException` is rethrown as-is, and any *other* exception type (e.g. a
connection failure) isn't caught at all — both fall through to the generic `Throwable` handler in
`ExceptionHandler.kt` as an uncoded HTTP 500.

Changed to: 404 stays `null` (genuinely absent from that repository); every other failure —
non-404 HTTP response *or* an `IOException` raised while querying that one repository (timeout,
connection refused, DNS failure, etc. — `HttpResponseException` is itself an `IOException`
subtype, so one `catch (e: IOException)` after the 404 check covers both) — throws
`ArtifactStoreUnavailableException`, naming the repository, the path, and the root cause.
Deliberately **not** `catch (e: Exception)`: that would also catch a programming error (e.g. an
NPE) and misreport it as "Artifactory unavailable" instead of letting it surface as a bug.
`IOException` is the narrowest type that covers every failure mode the guarded call
(`client.repository(it).file(...).info<File>()`) can realistically produce from a real network
call, without also catching unrelated `RuntimeException`s.

This intentionally does **not** try to distinguish "this one repository is unavailable" from "try
the next repository anyway" — same as today's behavior for a non-404 (`else throw e`), a failure
querying any one configured repository aborts the whole lookup immediately. All repositories share
the same Artifactory host/credentials, so a connectivity or auth problem on one will reproduce on
every other one; there's nothing to gain by continuing, and continuing risks masking the real
problem behind a later, unrelated 404.

### 3. Message content

Both messages are built by small private helper functions on `StorageServiceImpl`, so they can be
unit-tested as pure string-formatting logic without needing a live or mocked Artifactory call for
every case.

**Not-found**, replacing the previous message in `get()`/`notFound()`:
```
Artifact '$path' was not found in any of the repositories $repositories. This usually means
either it was never published to Artifactory before this validation step ran, or the
coordinates/version given to validation don't exactly match what was published — check the
groupId/artifactId/version/packaging (or image/tag for Docker).
```

**Unavailable**, new, thrown from `find()`/`storeUnavailable()`. The lead-in is shared; the closing
sentence depends on whether the failure is one Artifactory can recover from on its own:
```
Unable to check whether artifact '$path' exists in repository '$repository': $rootCause.
Artifactory itself could not be queried (connectivity, timeout or DNS problem).
```
```
Unable to check whether artifact '$path' exists in repository '$repository': $rootCause.
Artifactory rejected DMS's own credentials for that repository — this is a DMS configuration
problem, so retrying will not help.
```
`$rootCause` is the caught exception's own message (falling back to its `toString()` if the
message is null), so e.g. an `HttpResponseException` reports `"HTTP 500: ..."`-shaped text and a
connection failure reports the underlying IO error text.

The second variant is selected when the caught exception is an `HttpResponseException` whose status
is 401, 403 or 407 — Artifactory refusing DMS's credentials, or a proxy refusing them. That is a
deployment-config problem someone has to fix; lumping it in with "could not be queried" would tell
the reader to wait for a transient blip that is never going to clear. Every other non-404 status,
and every plain `IOException`, keeps the first variant. Both still map to
`ArtifactStoreUnavailableException`/`DMS-40015`/HTTP 503: nothing in the codebase branches on error
code (`DmsClientErrorDecoder` reconstructs an exception from the code and callers act on the
message), so a second exception type and code would add API surface no caller would read — the
distinction belongs in the sentence a human reads, not in a new code.

Both messages are deliberately generic across artifact types (Maven/Docker/RPM/DEB) — `$path`
already carries the type-specific structure (GAV path, `image/tag`, etc.), so no per-type message
variant is needed.

### 4. Testability seam: inject the `Artifactory` client

`StorageServiceImpl` currently builds its `Artifactory` client inline, in the primary constructor:
```kotlin
private val client: Artifactory = ArtifactoryClientBuilder.create()...build()
```
This makes `find()`'s branching (404 vs. other-HTTP vs. other-exception) untestable without a real
or WireMock'd Artifactory server. Changed to accept `Artifactory` as a constructor parameter,
defaulted to the same builder expression:
```kotlin
class StorageServiceImpl(
    private val storageProperties: StorageProperties,
    private val client: Artifactory = ArtifactoryClientBuilder.create()
        .setUrl("${storageProperties.artifactory.host}/artifactory")
        .setIgnoreSSLIssues(storageProperties.artifactory.trustAllCerts)
        .setUsername(storageProperties.artifactory.user)
        .setPassword(storageProperties.artifactory.password)
        .build(),
) : StorageService, HealthIndicator {
```
Spring still autowires the single real bean in production (only one constructor, and the second
parameter has a default Spring won't need to satisfy from the context — same wiring behavior as
today). A unit test can now pass a Mockito mock of `Artifactory` (an interface, as are
`Repository`/`ArtifactoryRequest`/`File` in the jfrog client library) to exercise `find()`/`get()`'s
branches directly.

### 5. Client-side aggregation (`client/maven-dms-plugin`, `ArtifactServiceImpl.processArtifacts`)

Today:
```java
if (!exceptions.isEmpty()) {
    exceptions.forEach(log::error);
    throw new MojoFailureException(exceptions.size() + " exception(s) occurred");
}
```
`log::error` resolves to Maven's `Log.error(Throwable)` overload, which prints the *full stack
trace* of each collected exception — this is the source of the wall of noise a failed run
produces (one full stack trace per failed artifact). The final `MojoFailureException`'s message —
`"N exception(s) occurred"` — is the only summary text a caller actually sees, and it carries none
of the underlying detail.

Changed to:
```java
if (!exceptions.isEmpty()) {
    List<String> messages = new ArrayList<>(exceptions.size());
    for (Exception e : exceptions) {
        String message = describeFailure(e);
        messages.add(message);
        log.error(message);
        log.debug(message, e);
    }
    throw new MojoFailureException(String.format("%d of %d artifact(s) failed:%n%s",
            exceptions.size(), results.size(), String.join("\n", messages)));
}
```
The summary says "failed", not "failed validation": `processArtifacts` is shared by
`ValidateArtifactsMojo` and `UploadArtifactsMojo`, so a goal-specific word here would misreport
every `upload-artifacts` failure.
`describeFailure` unwraps the `ExecutionException` that `Future.get()` adds, then appends the root
cause's message when the wrapper's own message doesn't already contain it — without it, an
`ExecutionException`'s `toString()` would be the only text a caller sees.
Each failure logs its own (already-actionable, per Decisions 1–3) message once at ERROR; the full
stack trace is still available, but only at DEBUG (`-X`/`--debug`), for whoever needs it. The
`MojoFailureException`'s message — the summary text a caller actually sees — is now the
concatenation of every failed artifact's own message, not a bare count.

### 6. Decision 5 is verified through the FT suite, not a unit test

`ft/src/ft/kotlin/.../DmsServiceApplicationFunctionalTest.kt` has a `runMavenDmsPlugin` helper that
runs the real `mvn ... validate-artifacts` goal as a subprocess and captures its full console
output (exit code + lines); several existing tests already assert on exact `[ERROR]`/`[INFO]`
lines from that real output (e.g. `testMavenDmsPluginValidateArtifactsDifferentRepos`). A new test,
`testMavenDmsPluginValidateArtifactsArtifactNotFound`, uses the same helper: it runs the goal
against coordinates that don't exist (so the real server-side `UnableToFindArtifactException` from
Decision 3 fires for real), and asserts what the fix is actually about — no stack-trace lines in
the output, the actionable message text is present, and the composed `"N of M artifact(s) failed"`
summary is present — using substring checks (`.any { it.contains(...) }`) rather than
exact-line matching, since the composed message's exact rendering (repository-set order,
coordinate `toString()`) isn't worth pinning down precisely for what this test needs to prove.

This exercises the real Maven `Log` implementation, the real `ExecutorService`, and the real
console rendering a caller actually sees — a more faithful check of Decision 5's behavior than a
unit test with a hand-rolled fake `Log` could give, and keeps `client/maven-dms-plugin` with no
dedicated unit-test tree, consistent with this repo's existing convention of verifying
maven-dms-plugin behavior through the FT suite.

### 7. `DMSException` carries an optional cause, so the server log keeps the real stack trace

Decision 2 catches Artifactory's own `IOException` and throws a new exception in its place. Without
a cause, the original is discarded: `ExceptionHandler`'s `logger.error(exception.message, exception)`
would log a trace that stops inside `StorageServiceImpl`, and the only surviving trace of *where*
the HTTP client actually failed is the one line of `cause.message` interpolated into the text. Before
this change a non-404 propagated raw to the catch-all handler and was logged in full, so dropping the
cause would be a real loss of server-side diagnosability.

```kotlin
abstract class DMSException(
    message: String,
    val code: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

The parameter is last and defaulted, so every existing subclass and every `CODE_EXCEPTION_MAP` entry
compiles and behaves unchanged. `ArtifactStoreUnavailableException` takes the same optional `cause`
and `storeUnavailable()` passes the caught `IOException` into it.

Nothing changes on the wire: `ApplicationErrorResponse` carries only `code`/`detail`/`message`, so a
client still receives one sentence and reconstructs a cause-less exception from the code — which is
the split we want. Full chain in the server log for whoever operates DMS; one actionable sentence
for the component owner running the build.

## Risks / Trade-offs

- **The default-parameter constructor seam** (Decision 4) is a small, deliberate widening of
  `StorageServiceImpl`'s public constructor surface purely for testability. It doesn't change
  production wiring or add a new Spring bean.
- **`ArtifactStoreUnavailableException` can't retry itself.** This change only makes the failure
  legible (correct error code, correct message, correct HTTP status) — it does not add retry logic
  to `find()`/`get()`. If Artifactory is genuinely flaky, the validate-artifacts step will still
  fail on the first bad attempt; that's an intentional non-goal here, out of scope for a
  diagnostics-only change.
