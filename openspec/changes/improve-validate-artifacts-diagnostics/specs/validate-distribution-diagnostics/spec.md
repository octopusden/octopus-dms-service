## Purpose

Defines how DMS reports artifact-lookup failures during validation/registration (`StorageServiceImpl`
on the server, surfaced through `maven-dms-plugin`'s `validate-artifacts` goal), so that a failure
is legible and actionable to the component owner reading a TeamCity build result, without the
underlying system needing to know *why* an artifact is missing.

## ADDED Requirements

### Requirement: A confirmed-missing artifact's message names what to check

When an artifact is not found in any of the repositories configured for its type (every
configured repository responds 404), the server SHALL raise `UnableToFindArtifactException` with a
message that includes the artifact's path, the full list of repositories checked, and a
suggestion to verify the artifact was published before this step ran and that its
coordinates/version match what was actually published.

#### Scenario: Every configured repository responds 404

- **WHEN** `StorageServiceImpl.get()` is called for an artifact and every repository configured
  for its `RepositoryType` returns HTTP 404 for that path
- **THEN** `UnableToFindArtifactException` is thrown with a message naming the artifact's path,
  the repositories checked, and guidance to verify the artifact was published and that its
  coordinates/version are correct

### Requirement: A repository-lookup failure is distinguished from a confirmed-missing artifact

When checking whether an artifact exists in a repository fails for a reason other than a
confirmed 404 (a non-404 HTTP response, or any other `IOException` — connection, timeout, DNS —
while querying that repository), the server SHALL raise `ArtifactStoreUnavailableException` (not
`UnableToFindArtifactException`, and not an uncoded generic error), naming the repository, the
artifact path, and the underlying failure. A failure that is not an `IOException` (e.g. a
programming error) SHALL NOT be caught or reported as `ArtifactStoreUnavailableException` — it
propagates unchanged, so a real bug is never mislabeled as "Artifactory unavailable".

#### Scenario: Artifactory returns a non-404 error while checking a repository

- **WHEN** querying a configured repository for an artifact's path returns an HTTP status other
  than 200 or 404
- **THEN** `ArtifactStoreUnavailableException` is thrown, and the lookup does not continue on to
  the next configured repository

#### Scenario: A connection failure occurs while checking a repository

- **WHEN** querying a configured repository for an artifact's path fails with a connection,
  timeout, or other `IOException`
- **THEN** `ArtifactStoreUnavailableException` is thrown with the underlying error's message
  included, rather than the raw exception propagating uncaught

#### Scenario: A programming error while checking a repository is not mislabeled

- **WHEN** querying a configured repository for an artifact's path fails with an error that is
  not an `IOException` (e.g. a `NullPointerException`)
- **THEN** that error propagates unchanged — it is neither wrapped as
  `ArtifactStoreUnavailableException` nor otherwise reported as an Artifactory-availability problem

#### Scenario: The failure maps to a distinguishable HTTP status and error code

- **WHEN** `ArtifactStoreUnavailableException` reaches the server's REST layer
- **THEN** the response is HTTP 503 with error code `DMS-40015`, distinct from
  `UnableToFindArtifactException`'s HTTP 400 / `DMS-40006` and from the generic, uncoded 500 a
  previously-unhandled exception would have produced

### Requirement: The Maven plugin's failure message is composed of each artifact's own message

When `maven-dms-plugin`'s `validate-artifacts` goal fails one or more artifacts, the build failure
SHALL report each failed artifact's own message, and per-artifact stack traces SHALL NOT be logged
above DEBUG level.

#### Scenario: Multiple artifacts fail validation in one run

- **WHEN** N of M artifacts fail during `ArtifactServiceImpl.processArtifacts`
- **THEN** the thrown `MojoFailureException`'s message includes a summary count and every failed
  artifact's own message, concatenated

#### Scenario: Stack traces are demoted to debug

- **WHEN** an artifact fails validation
- **THEN** the build log contains one ERROR-level line with that failure's message, and the full
  stack trace is emitted only at DEBUG level (`-X`/`--debug`)
