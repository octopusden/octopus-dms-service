package org.octopusden.octopus.dms.configuration

import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage")
data class StorageProperties(
    val mavenGroupPrefix: String,
    val artifactory: ArtifactoryProperties
) {
    data class ArtifactoryProperties(
        val host: String,
        val externalRequestHost: String? = null,
        val user: String,
        val password: String,
        val uploadRepositories: Map<RepositoryType, String> = emptyMap(),
        val stagingRepositories: Map<RepositoryType, Set<String>> = emptyMap(),
        val releaseRepositories: Map<RepositoryType, Set<String>> = emptyMap(),
        val trustAllCerts: Boolean = false
    )
}
