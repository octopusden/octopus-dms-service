package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.dto.ComponentBuild
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.releasemanagementservice.client.ReleaseManagementServiceClient
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildDTO
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildFilterDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildStatus as rmServiceBuildStatus

@Service
class ReleaseManagementServiceImpl(
    private val client: ReleaseManagementServiceClient
) : ReleaseManagementService {
    private val artifactsAllowedStatuses = mapOf<ArtifactType?, List<rmServiceBuildStatus>>(
        ArtifactType.DISTRIBUTION to listOf(rmServiceBuildStatus.RELEASE)
    )
    private val defaultAllowedStatuses = listOf(rmServiceBuildStatus.RELEASE, rmServiceBuildStatus.RC)
    override fun validateVersionStatus(component: String, version: String, type: ArtifactType?) {
        try {
            val versionStatus = client.getBuild(component, version).status
            if (!artifactsAllowedStatuses.getOrDefault(type, defaultAllowedStatuses).contains(versionStatus)) {
                throw IllegalVersionStatusException("Status '$versionStatus' of version '$version' of component '$component' is illegal${type?.let { " for $it artifact" } ?: ""}")
            }
        } catch (e: org.octopusden.octopus.releasemanagementservice.client.common.exception.NotFoundException) {
            throw NotFoundException(e.message ?: "")
        }
    }

    override fun componentExists(component: String): Boolean {
        return try {
            client.getComponent(component).id == component
        } catch (e: org.octopusden.octopus.releasemanagementservice.client.common.exception.NotFoundException) {
            false
        }
    }

    override fun getComponentBuilds(component: String, buildStatuses: Array<BuildStatus>, versions: Set<String>)
            : List<ComponentBuild> {
        val statuses = buildStatuses.map { bs -> rmServiceBuildStatus.valueOf(bs.name) }.toSet()
        return client.getBuilds(component, BuildFilterDTO(statuses = statuses, versions = versions))
            .map { b -> ComponentBuild(BuildStatus.valueOf(b.status.name), b.version) }
    }

    override fun getComponentBuild(component: String, version: String) =
        client.getBuild(component, version).toComponentBuild()

    fun BuildDTO.toComponentBuild(): ComponentBuild = ComponentBuild(BuildStatus.valueOf(status.name), version)
}
