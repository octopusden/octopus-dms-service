package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus
import org.octopusden.octopus.dms.configuration.ReleaseManagementServiceProperties
import org.octopusden.octopus.dms.dto.BuildDTO
import org.octopusden.octopus.dms.dto.ReleaseDTO
import org.octopusden.octopus.dms.dto.ReleaseFullDTO
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildFilterDTO
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildStatus
import org.octopusden.octopus.releasemanagementservice.client.common.dto.ShortBuildDTO
import org.octopusden.octopus.releasemanagementservice.client.common.exception.NotFoundException
import org.octopusden.octopus.releasemanagementservice.client.impl.ClassicReleaseManagementServiceClient
import org.octopusden.octopus.releasemanagementservice.client.impl.ReleaseManagementServiceClientParametersProvider
import org.springframework.stereotype.Service

@Service
class ReleaseManagementServiceImpl(
    private val releaseManagementServiceProperties: ReleaseManagementServiceProperties
) : ReleaseManagementService {
    private val client = ClassicReleaseManagementServiceClient(
        object : ReleaseManagementServiceClientParametersProvider {
            override fun getApiUrl() = releaseManagementServiceProperties.url

            override fun getTimeRetryInMillis() = releaseManagementServiceProperties.retry
        }
    )

    override fun isComponentExists(component: String) = try {
        client.getComponent(component).id == component
    } catch (_: NotFoundException) {
        false
    }

    override fun findReleases(component: String, buildVersions: List<String>, includeRc: Boolean): List<ReleaseDTO> {
        val allowedStatuses = getAllowedStatuses(includeRc)
        return buildVersions.chunked(20).flatMap {
            client.getBuilds(component, BuildFilterDTO(statuses = allowedStatuses, versions = it.toSet()))
                .map { build ->
                    ReleaseDTO(
                        build.component,
                        build.version,
                        ComponentVersionStatus.valueOf(build.status.name)
                    )
                }
        }
    }

    override fun getRelease(component: String, version: String, includeRc: Boolean): ReleaseFullDTO {
        val allowedStatuses = getAllowedStatuses(includeRc)
        val build = client.getBuild(component, version)
        return if (allowedStatuses.contains(build.status)) ReleaseFullDTO(
            build.component,
            build.version,
            ComponentVersionStatus.valueOf(build.status.name),
            build.statusHistory[build.status],
            build.parents.map { it.toBuildDTO() },
            build.dependencies.map { it.toBuildDTO() }
        ) else throw IllegalVersionStatusException(
            "Build for version '$version' of component '$component' has status ${build.status}. Allowed statuses are $allowedStatuses"
        )
    }

    override fun findRelease(component: String, version: String, includeRc: Boolean) = try {
        getRelease(component, version, includeRc)
    } catch (_: Exception) {
        null
    }

    companion object {
        private val NO_LESS_THAN_RELEASE = BuildStatus.RELEASE.noLessThan()
        private val NO_LESS_THAN_RC = BuildStatus.RC.noLessThan()

        private fun getAllowedStatuses(includeRc: Boolean) = if (includeRc) NO_LESS_THAN_RC else NO_LESS_THAN_RELEASE

        private fun ShortBuildDTO.toBuildDTO() = BuildDTO(component, version)
    }
}
