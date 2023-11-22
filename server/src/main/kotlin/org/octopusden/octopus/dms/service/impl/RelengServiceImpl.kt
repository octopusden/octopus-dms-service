package org.octopusden.octopus.dms.service.impl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.configuration.RelengProperties
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.service.RelengService
import khttp.get
import khttp.responses.Response
import org.springframework.stereotype.Service

@Service
class RelengServiceImpl( //TODO: reimplement using RelengClient
    relengProperties: RelengProperties,
    private val objectMapper: ObjectMapper
) : RelengService {
    private val baseURL = "${relengProperties.host}/${relengProperties.apiUri}"
    private val versionStatusesAllowedByArtifactType = mapOf<ArtifactType?, List<BuildStatus>>(
        ArtifactType.DISTRIBUTION to listOf(BuildStatus.RELEASE)
    )
    private val versionStatusesAllowedByDefault = listOf(BuildStatus.RELEASE, BuildStatus.RC)

    override fun checkVersionStatus(component: String, version: String, type: ArtifactType?) {
        val versionStatus = get(
            url = "$baseURL/component/$component/version/$version/status"
        ).toObject(object : TypeReference<VersionStatus>() {}).versionStatus
        if (!versionStatusesAllowedByArtifactType.getOrDefault(type, versionStatusesAllowedByDefault).contains(versionStatus)) {
            throw IllegalVersionStatusException("Status '$versionStatus' of version '$version' of component '$component' is illegal${type?.let { " for $it artifact" } ?: ""}")
        }
    }

    override fun getComponentBuilds(
        component: String,
        buildStatuses: Array<BuildStatus>,
        versions: Array<String>,
        versionsField: VersionField
    ): List<ComponentBuild> {
        val params = mapOf(
            "build_whitelist" to "status,version,release_version",
            "version_statuses" to buildStatuses.joinToString(separator = ","),
            "versions_field" to versionsField.name
        )
        return if (versions.isEmpty()) {
            getComponentBuilds(component, params)
        } else {
            versions.toList().chunked(20).flatMap {
                getComponentBuilds(component, params + mapOf("versions" to it.joinToString(",")))
            }
        }
    }

    private fun getComponentBuilds(component: String, params: Map<String, String>) = get(
        url = "$baseURL/components/$component",
        params = params
    ).toObject(object : TypeReference<ComponentBuilds>() {}).builds

    private fun <T> Response.toObject(typeReference: TypeReference<T>): T {
        if (this.statusCode / 100 != 2) {
            if (this.statusCode == 404) {
                throw NotFoundException(this.text)
            } else {
                throw RuntimeException(this.text)
            }
        }
        return objectMapper.readValue(this.text, typeReference)
    }
}

data class VersionStatus(
    val component: String,
    val version: String,
    val buildVersion: String,
    val releaseVersion: String,
    val versionStatus: BuildStatus
)

data class ComponentBuilds(
    val name: String,
    val builds: List<ComponentBuild>
)

data class ComponentBuild(
    val status: BuildStatus,
    val version: String,
    @JsonProperty("release_version")
    val releaseVersion: String
)

enum class VersionField {
    VERSION,
    RELEASE_VERSION
}
