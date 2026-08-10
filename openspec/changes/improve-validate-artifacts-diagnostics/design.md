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
that talks to DMS (`DMSServiceImpl.java:60`, `ArtifactServiceImpl.java:196` in
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
non-404 HTTP response *or* any other exception raised while querying that one repository — throws
`ArtifactStoreUnavailableException`, naming the repository, the path, and the root cause. The
catch is broadened from `HttpResponseException` to `Exception` because the guarded call
(`client.repository(it).file(...).info<File>()`) does exactly one thing — query one repository —
so a broad catch there is scoped, not a blanket suppression.

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

**Not-found** (replaces the message at `StorageServiceImpl.kt:82`):
```
Artifact '$path' was not found in any of the repositories $repositories. This usually means
either it was never published to Artifactory before this validation step ran, or the
coordinates/version given to validation don't exactly match what was published — check the
groupId/artifactId/version/packaging (or image/tag for Docker) and the publish step that should
have produced it.
```

**Unavailable** (new, thrown from `find()`):
```
Unable to check whether artifact '$path' exists in repository '$repository': $rootCause.
Artifactory itself could not be queried (connectivity, credentials, or permission problem) — this
does not confirm the artifact is missing. Check Artifactory availability/credentials and retry.
```
`$rootCause` is the caught exception's own message (falling back to its `toString()` if the
message is null), so e.g. an `HttpResponseException` reports `"HTTP 401: ..."`-shaped text and a
connection failure reports the underlying IO error text.

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
trace* of each collected exception — this is the actual source of the wall of noise in the
original log (six ~35-line stack traces back to back). The final `MojoFailureException`'s message
— `"N exception(s) occurred"` — is what TeamCity's build-status-problem line actually shows, and
it carries none of the underlying detail.

Changed to:
```java
if (!exceptions.isEmpty()) {
    exceptions.forEach(e -> {
        log.error(e.getMessage() != null ? e.getMessage() : e.toString());
        log.debug(e.getMessage(), e);
    });
    String summary = exceptions.stream()
            .map(e -> e.getMessage() != null ? e.getMessage() : e.toString())
            .collect(Collectors.joining("\n"));
    throw new MojoFailureException(String.format("%d of %d artifact(s) failed validation:%n%s",
            exceptions.size(), results.size(), summary));
}
```
Each failure logs its own (already-actionable, per Decisions 1–3) message once at ERROR; the full
stack trace is still available, but only at DEBUG (`-X`/`--debug`), for whoever needs it. The
`MojoFailureException`'s message — the text TeamCity surfaces — is now the concatenation of every
failed artifact's own message, not a bare count.

### 6. First tests in `client/maven-dms-plugin`

This module (`client/maven-dms-plugin/build.gradle.kts`) has no `src/test` directory and no test
dependencies declared today (confirmed: no other module in this repo needed to add them from
scratch except by following the existing `test-common`/`ft` pattern). `useJUnitPlatform()` is
already applied repo-wide (`build.gradle.kts:132`, in the `subprojects` block), so only the
library dependencies are missing. Add, mirroring `test-common/build.gradle.kts:5-7`:
```kotlin
dependencies {
    testImplementation(platform("org.junit:junit-bom:${project.properties["junit.version"]}"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}
```

## Risks / Trade-offs

- **Broadening `find()`'s catch to `Exception`** could, in principle, swallow a programming error
  (e.g. an NPE) into a misleading "Artifactory unavailable" message instead of surfacing it as a
  bug. Accepted: the guarded call is a single, narrow Artifactory client invocation with no other
  logic inside the try block, so there's nothing else that call is realistically expected to throw.
- **The default-parameter constructor seam** (Decision 4) is a small, deliberate widening of
  `StorageServiceImpl`'s public constructor surface purely for testability. It doesn't change
  production wiring or add a new Spring bean.
- **`ArtifactStoreUnavailableException` can't retry itself.** This change only makes the failure
  legible (correct error code, correct message, correct HTTP status) — it does not add retry logic
  to `find()`/`get()`. If Artifactory is genuinely flaky, the validate-artifacts step will still
  fail on the first bad attempt; that's an intentional non-goal here, out of scope for a
  diagnostics-only change.
