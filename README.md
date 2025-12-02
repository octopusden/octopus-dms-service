# Distribution Management System (for publishing, managing, downloading artifact)

## JDK version

21

## Maven version

3.3.9

> NOTE: Build requires either environment variable `M2_HOME`/`MAVEN_HOME` defined or `mvn` available in PATH

## Project properties

| Name                           | Description                                                      | UT    | FT    | Release |
|--------------------------------|------------------------------------------------------------------|-------|-------|---------|
| docker.registry                | Docker registry where 3rd-party base images will be pulled from. | **+** | **+** | **+**   |
| octopus.github.docker.registry | Docker registry with octopus images.                             |       | **+** | **+**   |
| auth-server.url                | Auth server URL.                                                 | **+** | **+** |         |
| auth-server.realm              | Auth server realm.                                               | **+** | **+** |         |
| auth-server.client-id          | octopus-api-gateway client Id.                                   |       | **+** |         |
| auth-server.client-secret      | octopus-api-gateway client secret.                               |       | **+** |         |
| dms-service.user               | dms-service user.                                                |       | **+** |         |
| dms-service.password           | dms-service user password.                                       |       | **+** |         |

## Updating artifactory test-data

* Run `:dms-service:importArtifactoryDump` gradle task
* Use Artifactory UI to manage test repositories/artifacts
    * URL: http://localhost:8082/ui/
    * Login: `admin`
    * Password: `password`
* Execute `chmod -R 777` for `test-common/src/main/artifactory/dump`
* Perform system export
    * URL: http://localhost:8082/ui/admin/artifactory/import_export/system
    * Export Path on Server: `/dump`
    * Create .m2 Compatible Export: `Yes`
* Execute `chmod -R 777` for `test-common/src/main/artifactory/dump`
* Remove old dump from `test-common/src/main/artifactory/dump`
* Run `:dms-service:composeDown` gradle task
