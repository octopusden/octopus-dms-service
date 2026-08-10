package org.octopusden.octopus.dms.service.impl

import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.File
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.configuration.StorageProperties
import org.octopusden.octopus.dms.exception.ArtifactStoreUnavailableException
import org.octopusden.octopus.dms.exception.GeneralArtifactStoreException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import org.octopusden.octopus.dms.service.StorageService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.InputStream

@Service
class StorageServiceImpl(
    private val storageProperties: StorageProperties,
    private val client: Artifactory = ArtifactoryClientBuilder
        .create()
        .setUrl("${storageProperties.artifactory.host}/artifactory")
        .setIgnoreSSLIssues(storageProperties.artifactory.trustAllCerts)
        .setUsername(storageProperties.artifactory.user)
        .setPassword(storageProperties.artifactory.password)
        .build(),
) : StorageService,
    HealthIndicator {
    private fun getRepositories(
        repositoryType: RepositoryType,
        includeStaging: Boolean,
    ) = (storageProperties.artifactory.uploadRepositories[repositoryType]?.let { setOf(it) } ?: emptySet()) +
        (if (includeStaging) storageProperties.artifactory.stagingRepositories[repositoryType] ?: emptySet() else emptySet()) +
        (storageProperties.artifactory.releaseRepositories[repositoryType] ?: emptySet()) +
        (storageProperties.artifactory.coldRepositories[repositoryType] ?: emptySet())

    override fun getRepositoriesUrls(
        repositoryType: RepositoryType,
        includeStaging: Boolean,
    ) = getRepositories(repositoryType, includeStaging).map {
        "${storageProperties.artifactory.externalRequestHost ?: storageProperties.artifactory.host}/artifactory/$it"
    }

    override fun upload(
        repositoryType: RepositoryType,
        path: String,
        inputStream: InputStream,
    ): File =
        client
            .repository(
                storageProperties.artifactory.uploadRepositories[repositoryType]
                    ?: throw GeneralArtifactStoreException("Upload repository for $repositoryType artifacts is not set"),
            ).upload(path, inputStream)
            .doUpload()

    override fun find(
        repositoryType: RepositoryType,
        includeStaging: Boolean,
        path: String,
    ) = getRepositories(repositoryType, includeStaging).firstNotNullOfOrNull { repository ->
        try {
            client
                .repository(repository)
                .file(
                    if (repositoryType == RepositoryType.DOCKER) {
                        "$path/manifest.json"
                    } else {
                        path
                    },
                ).info<File>()
        } catch (e: HttpResponseException) {
            if (e.statusCode == 404) null else throw storeUnavailable(repository, path, e)
        } catch (e: IOException) {
            throw storeUnavailable(repository, path, e)
        }
    }

    override fun get(
        repositoryType: RepositoryType,
        includeStaging: Boolean,
        path: String,
    ) = find(repositoryType, includeStaging, path) ?: throw notFound(path, getRepositories(repositoryType, includeStaging))

    private fun notFound(
        path: String,
        repositories: Set<String>,
    ) = UnableToFindArtifactException(
        "Artifact '$path' was not found in any of the repositories $repositories. This usually means either it " +
            "was never published to Artifactory before this validation step ran, or the coordinates/version " +
            "given to validation don't exactly match what was published — check the groupId/artifactId/version/" +
            "packaging (or image/tag for Docker).",
    )

    private fun storeUnavailable(
        repository: String,
        path: String,
        cause: IOException,
    ) = ArtifactStoreUnavailableException(
        "Unable to check whether artifact '$path' exists in repository '$repository': " +
            "${cause.message ?: cause}. Artifactory itself could not be queried (connectivity, credentials, or " +
            "permission problem).",
    )

    override fun download(
        repositoryType: RepositoryType,
        includeStaging: Boolean,
        path: String,
    ): InputStream {
        if (repositoryType == RepositoryType.DOCKER) {
            throw UnsupportedOperationException("Downloading of $repositoryType artifacts is not supported.")
        }
        return client.repository(get(repositoryType, includeStaging, path).repo).download(path).doDownload()
    }

    override fun health(): Health =
        try {
            if (client.system().ping()) {
                Health.up().build()
            } else {
                Health.down().build()
            }
        } catch (e: Exception) {
            Health.down(e).build()
        }
}
