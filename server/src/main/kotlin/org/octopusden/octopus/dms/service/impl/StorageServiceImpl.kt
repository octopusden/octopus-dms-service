package org.octopusden.octopus.dms.service.impl

import java.io.InputStream
import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.File
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.configuration.StorageProperties
import org.octopusden.octopus.dms.exception.GeneralArtifactStoreException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import org.octopusden.octopus.dms.service.StorageService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Service

@Service
class StorageServiceImpl(
    private val storageProperties: StorageProperties
) : StorageService, HealthIndicator {

    private val client: Artifactory = ArtifactoryClientBuilder.create()
        .setUrl("${storageProperties.artifactory.host}/artifactory")
        .setIgnoreSSLIssues(storageProperties.artifactory.trustAllCerts)
        .setUsername(storageProperties.artifactory.user)
        .setPassword(storageProperties.artifactory.password)
        .build()

    private fun getRepositories(repositoryType: RepositoryType, includeStaging: Boolean) =
        (storageProperties.artifactory.uploadRepositories[repositoryType]?.let { setOf(it) } ?: emptySet()) +
            (if (includeStaging) storageProperties.artifactory.stagingRepositories[repositoryType] ?: emptySet() else emptySet()) +
            (storageProperties.artifactory.releaseRepositories[repositoryType] ?: emptySet())

    override fun getRepositoriesUrls(repositoryType: RepositoryType, includeStaging: Boolean) =
        getRepositories(repositoryType, includeStaging).map {
            "${storageProperties.artifactory.externalRequestHost ?: storageProperties.artifactory.host}/artifactory/$it"
        }

    override fun upload(repositoryType: RepositoryType, path: String, inputStream: InputStream): File =
        client.repository(
            storageProperties.artifactory.uploadRepositories[repositoryType]
                ?: throw GeneralArtifactStoreException("Upload repository for $repositoryType artifacts is not set")
        ).upload(path, inputStream).doUpload()

    override fun find(repositoryType: RepositoryType, includeStaging: Boolean, path: String): File {
        val repositories = getRepositories(repositoryType, includeStaging)
        repositories.forEach {
            try {
                return client.repository(it).file(
                    if (repositoryType == RepositoryType.DOCKER) {
                        "$path/manifest.json"
                    } else {
                        path
                    }
                ).info()
            } catch (e: HttpResponseException) {
                if (e.statusCode != 404) throw e
            }
        }
        throw UnableToFindArtifactException("Artifact $path not found in repositories $repositories")
    }

    override fun download(repositoryType: RepositoryType, includeStaging: Boolean, path: String): InputStream {
        if (repositoryType == RepositoryType.DOCKER) {
            throw UnsupportedOperationException("Downloading of $repositoryType artifacts is not supported.")
        }
        return client.repository(find(repositoryType, includeStaging, path).repo).download(path).doDownload()
    }

    override fun health(): Health {
        return try {
            if (client.system().ping()) {
                Health.up().build()
            } else {
                Health.down().build()
            }
        } catch (e: Exception) {
            Health.down(e).build()
        }
    }
}
