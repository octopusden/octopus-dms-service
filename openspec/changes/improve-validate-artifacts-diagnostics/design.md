## Context

`StorageServiceImpl` (server) is the only place that talks to Artifactory when validating/adding an
artifact.

- `get()` calls `find()`, which walks the configured repositories for a `RepositoryType`
  (upload/staging/release/cold) and returns the first `File` info found, or `null` if every
  repository 404s.
- `get()` turns a `null` into `UnableToFindArtifactException`.

How a `DMSException` reaches the caller:

- It crosses the server→client boundary as JSON `{code, detail, message}` (`ApplicationErrorResponse`).
- `DmsClientErrorDecoder` reconstructs it from `DMSException.CODE_EXCEPTION_MAP` by `code` alone.
- Every `maven-dms-plugin` mojo call site catches it generically as `Exception`.

Consequence that shapes several decisions below: **no caller branches on the error code.** The code
selects which exception class to rebuild; what a human acts on is the message.

## Decisions

### 1. New exception: `ArtifactStoreUnavailableException` (DMS-40015 → HTTP 503)

Added to `common/.../exception/ServicesExceptions.kt`.

- Code `DMS-40015` — the next free code. `DMS-40005`, `-40009`, `-40010` are historical gaps and are
  not reused.
- Carries an optional `cause` (see §7). Every other subclass stays message-only.
- Mapped in `ExceptionHandler.kt` by its own `@ExceptionHandler` method →
  `HttpStatus.SERVICE_UNAVAILABLE`.

Why its own status rather than folding into the existing `BAD_REQUEST` group:

- That group means "the request was well-formed but factually wrong" — bad coordinates, already
  exists, and so on.
- This one means "DMS couldn't determine an answer because its dependency failed."
- A monitoring dashboard or an automated retry would want to treat those differently.

No client-side wiring needed beyond the `CODE_EXCEPTION_MAP` entry:

- `DmsClientErrorDecoder.decode` (`client/client/.../DmsClientErrorDecoder.kt:24-26`) reconstructs by
  `code` from the JSON body, independent of HTTP status.
- Every mojo call site that talks to DMS (`DMSServiceImpl.java:60`,
  `ArtifactServiceImpl.java:197` in `client/maven-dms-plugin`) already catches `Exception`.

### 2. `StorageServiceImpl.find()` distinguishes "confirmed absent" from "couldn't check"

Today:
```kotlin
} catch (e: HttpResponseException) {
    if (e.statusCode == 404) null else throw e
}
```

- A non-404 `HttpResponseException` is rethrown as-is.
- Any *other* exception type (a connection failure, say) isn't caught at all.
- Both land in the generic `Throwable` handler as an uncoded HTTP 500.

Changed to:

- **404** → still `null`. Genuinely absent from that repository.
- **Any other non-404 HTTP status, or an `IOException` raised while querying that one repository**
  (timeout, connection refused, DNS) → `ArtifactStoreUnavailableException`, naming the repository,
  the path, and the root cause. One `catch (e: IOException)` after the 404 check covers both, since
  `HttpResponseException` is itself an `IOException` subtype.

Deliberately **not** `catch (e: Exception)`:

- That would also catch a programming error (an NPE, say) and misreport it as "Artifactory
  unavailable" instead of letting it surface as a bug.
- `IOException` is the narrowest type covering every failure mode the guarded call
  (`client.repository(it).file(...).info<File>()`) can realistically produce from a real network call,
  without catching unrelated `RuntimeException`s.

A failure on any one repository aborts the whole lookup — it does **not** try the next repository.
Same as today's `else throw e`, and deliberate:

- All repositories share the same Artifactory host and credentials, so a connectivity or auth problem
  on one reproduces on every other.
- Continuing risks masking the real problem behind a later, unrelated 404.

### 3. Message content

Both messages are built by small private helpers on `StorageServiceImpl`, so they can be unit-tested
as pure string formatting without a live or mocked Artifactory call for every case.

**Not-found** — replaces the previous message in `get()`/`notFound()`:
```
Artifact '$path' was not found in any of the repositories $repositories. This usually means
either it was never published to Artifactory before this validation step ran, or the
coordinates/version given to validation don't exactly match what was published — check the
groupId/artifactId/version/packaging (or image/tag for Docker).
```

**Unavailable** — new, from `find()`/`storeUnavailable()`. Shared lead-in, then one of two closing
sentences depending on whether the failure can clear on its own:

```
Unable to check whether artifact '$path' exists in repository '$repository': $rootCause.
Artifactory itself could not be queried (connectivity, timeout or DNS problem).
```
```
Unable to check whether artifact '$path' exists in repository '$repository': $rootCause.
Artifactory rejected DMS's own credentials for that repository — this is a DMS configuration
problem, so retrying will not help.
```

Which variant, and why:

- `$rootCause` is the caught exception's own message, falling back to `toString()` if null. So an
  `HttpResponseException` reports `"HTTP 500: ..."`-shaped text and a connection failure reports the
  underlying IO error text.
- The second variant is selected for an `HttpResponseException` with status **401, 403 or 407** —
  Artifactory refusing DMS's credentials, or a proxy refusing them.
- That is a deployment-config problem someone has to fix. Calling it "could not be queried" would
  tell the reader to wait out a transient blip that is never going to clear.
- Every other non-404 status, and every plain `IOException`, keeps the first variant.

Both variants still map to `ArtifactStoreUnavailableException` / `DMS-40015` / HTTP 503, rather than
introducing a second exception type and code:

- Nothing in the codebase branches on error code (see Context).
- A second code would add API surface no caller reads.
- The distinction belongs in the sentence a human reads.

Both messages are deliberately generic across artifact types (Maven/Docker/RPM/DEB) — `$path`
already carries the type-specific structure (GAV path, `image/tag`), so no per-type variant is needed.

### 4. Testability seam: inject the `Artifactory` client

`StorageServiceImpl` built its client inline in the primary constructor:
```kotlin
private val client: Artifactory = ArtifactoryClientBuilder.create()...build()
```
That makes `find()`'s branching (404 vs. other-HTTP vs. other-exception) untestable without a real or
WireMock'd Artifactory. Changed to a constructor parameter, defaulted to the same expression:
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

- Production wiring is unchanged: one constructor, and Spring resolves the Kotlin optional parameter
  from its default rather than looking for an `Artifactory` bean. Verified against the real test
  classpath, not assumed — an unresolvable optional parameter would fail context startup outright.
- A unit test can now pass a Mockito mock of `Artifactory` (an interface, as are
  `Repository`/`ArtifactoryRequest`/`File` in the jfrog client) and drive `find()`/`get()` directly.

### 5. Client-side aggregation (`client/maven-dms-plugin`, `ArtifactServiceImpl.processArtifacts`)

Today:
```java
if (!exceptions.isEmpty()) {
    exceptions.forEach(log::error);
    throw new MojoFailureException(exceptions.size() + " exception(s) occurred");
}
```

- `log::error` resolves to Maven's `Log.error(Throwable)` overload, which prints the *full stack
  trace* of each collected exception — one per failed artifact. This is the wall of noise.
- `"N exception(s) occurred"` is the only summary text a caller sees, and it carries no detail.

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

- Each failure logs its own (already-actionable, per §1–§3) message once at ERROR. The full stack
  trace is still there, but only at DEBUG (`-X`/`--debug`).
- The `MojoFailureException` message is the concatenation of every failed artifact's own message, not
  a bare count.
- `describeFailure` unwraps the `ExecutionException` that `Future.get()` adds, then appends the root
  cause's message when the wrapper's own message doesn't already contain it. Without it, an
  `ExecutionException`'s `toString()` would be the only text a caller sees.
- The summary says "failed", **not** "failed validation": `processArtifacts` backs both
  `ValidateArtifactsMojo` and `UploadArtifactsMojo`, so a goal-specific word would misreport every
  `upload-artifacts` failure.

### 6. §5 is verified through the FT suite, not a unit test

`DmsServiceApplicationFunctionalTest.kt`'s `runMavenDmsPlugin` helper runs the real
`mvn ... validate-artifacts` goal as a subprocess and captures exit code plus console lines; existing
tests already assert on real `[ERROR]`/`[INFO]` output.

`testMavenDmsPluginValidateArtifactsArtifactNotFound` uses the same helper against coordinates that
don't exist, so the real server-side `UnableToFindArtifactException` fires, and asserts:

- No per-artifact exception dump — the DMS exception's FQCN is absent from the output.
- The actionable not-found text is present.
- The composed `"N of M artifact(s) failed"` summary is present.

Two things to know about those assertions:

- Substring checks (`.any { it.contains(...) }`) rather than exact-line matching. The composed
  message's exact rendering (repository-set order, coordinate `toString()`) isn't worth pinning down.
- The check is on the **exception class name**, not on `"\tat "` lines. `runMavenDmsPlugin` passes
  `-e`, so Maven always prints one stack trace for the `LifecycleExecutionException` /
  `MojoFailureException` pair no matter what the plugin logs. What regressed before was the plugin
  dumping each DMS exception as a throwable, which put its FQCN in the output; message-only logging
  never does.

Why FT rather than a unit test:

- It exercises the real Maven `Log`, the real `ExecutorService`, and the real console rendering.
- It keeps `client/maven-dms-plugin` with no dedicated unit-test tree, consistent with how
  maven-dms-plugin behavior is verified in this repo.

### 7. `DMSException` carries an optional cause, so the server log keeps the real stack trace

§2 catches Artifactory's own `IOException` and throws a new exception in its place. Without a cause:

- `ExceptionHandler`'s `logger.error(exception.message, exception)` logs a trace that stops inside
  `StorageServiceImpl`.
- The only surviving evidence of *where* the HTTP client failed is the one line of `cause.message`
  interpolated into the text.
- Before this change a non-404 propagated raw to the catch-all handler and was logged in full — so
  dropping the cause would be a real regression in server-side diagnosability.

```kotlin
abstract class DMSException(
    message: String,
    val code: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

- The parameter is last and defaulted, so every existing subclass and every `CODE_EXCEPTION_MAP`
  entry compiles and behaves unchanged.
- `ArtifactStoreUnavailableException` takes the same optional `cause`; `storeUnavailable()` passes
  the caught `IOException` in.
- Nothing changes on the wire: `ApplicationErrorResponse` carries only `code`/`detail`/`message`, so
  a client receives one sentence and reconstructs a cause-less exception from the code.

That split is the point: full chain in the server log for whoever operates DMS, one actionable
sentence for the component owner running the build.

## Risks / Trade-offs

- **The default-parameter constructor seam** (§4) widens `StorageServiceImpl`'s public constructor
  surface purely for testability. It doesn't change production wiring or add a Spring bean.
- **`ArtifactStoreUnavailableException` can't retry itself.** This change makes the failure legible —
  right code, right message, right HTTP status. It adds no retry logic to `find()`/`get()`. If
  Artifactory is genuinely flaky, validate-artifacts still fails on the first bad attempt. Intentional
  non-goal for a diagnostics-only change.
- **The not-found message is worded for the validation path, but `get()` has three callers.** It also
  serves `download()` (a consumer fetching a distribution) and the checksum re-check in
  `ComponentServiceImpl.registerArtifact`. Neither of those ran "validation", so a consumer whose
  download 404s is told to check coordinates they never supplied. Accepted knowingly: the validation
  path is where this diagnostic actually gets read, and generic wording would blunt it there. Dropping
  the two mentions of "validation" would make it accurate everywhere if that ever bites.
- **401/403/407 still map to HTTP 503**, which conventionally means "retry later" — the wrong advice
  for a config error. Only the message distinguishes them (§3). Acceptable because no caller branches
  on status or code, but if one ever does, this is the seam to revisit.
