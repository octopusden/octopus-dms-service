package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.BuildDTO
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.dto.ComponentBuild
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.releasemanagementservice.client.ReleaseManagementServiceClient
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildFilterDTO
import org.springframework.stereotype.Service
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildDTO as RmBuildDTO
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildStatus as rmServiceBuildStatus

@Service
class ReleaseManagementServiceImpl(
    private val client: ReleaseManagementServiceClient
) : ReleaseManagementService {
    private val artifactsAllowedStatuses = mapOf<ArtifactType?, List<rmServiceBuildStatus>>(
        ArtifactType.DISTRIBUTION to listOf(rmServiceBuildStatus.RELEASE)
    )
    private val defaultAllowedStatuses = listOf(rmServiceBuildStatus.RELEASE, rmServiceBuildStatus.RC)

    override fun getComponentBuild(component: String, version: String, type: ArtifactType?): BuildDTO {
        return try {
           client.getBuild(component, version).also { build ->
               val versionStatus = build.status
               if (!artifactsAllowedStatuses.getOrDefault(type, defaultAllowedStatuses).contains(versionStatus)) {
                   throw IllegalVersionStatusException("Status '$versionStatus' of version '$version' of component '$component' is illegal${type?.let { " for $it artifact" } ?: ""}")
               }
            }.toBuildDTO()
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
        return versions.chunked(20)
            .flatMap { chunkVersions ->
                client.getBuilds(component, BuildFilterDTO(statuses = statuses, versions = chunkVersions.toSet()))
                    .map { b -> ComponentBuild(BuildStatus.valueOf(b.status.name), b.version) }
            }
    }

    override fun getComponentBuild(component: String, version: String) =
        try {
            client.getBuild(component, version).toComponentBuild()
        } catch (e: org.octopusden.octopus.releasemanagementservice.client.common.exception.NotFoundException) {
            throw NotFoundException(e.message ?: "")
        }


    fun RmBuildDTO.toComponentBuild(): ComponentBuild = ComponentBuild(BuildStatus.valueOf(status.name), version)

    fun RmBuildDTO.toBuildDTO(): BuildDTO {
        val buildStatus = BuildStatus.valueOf(status.name)
        val promotedAt = statusHistory[status]
        return BuildDTO(component, version, buildStatus, promotedAt)
    }
}
