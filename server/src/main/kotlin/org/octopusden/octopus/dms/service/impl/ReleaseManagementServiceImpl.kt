package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus
import org.octopusden.octopus.dms.dto.DependencyDTO
import org.octopusden.octopus.dms.dto.ReleaseDTO
import org.octopusden.octopus.dms.dto.ReleaseFullDTO
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.releasemanagementservice.client.ReleaseManagementServiceClient
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildFilterDTO
import org.octopusden.octopus.releasemanagementservice.client.common.dto.BuildStatus
import org.springframework.stereotype.Service
import org.octopusden.octopus.releasemanagementservice.client.common.exception.NotFoundException as RmNotFoundException

@Service
class ReleaseManagementServiceImpl(
    private val client: ReleaseManagementServiceClient
) : ReleaseManagementService {
    override fun isComponentExists(component: String) = try {
        client.getComponent(component).id == component
    } catch (_: RmNotFoundException) {
        false
    }

    override fun findReleases(component: String, versions: List<String>, includeRc: Boolean): List<ReleaseDTO> {
        val allowedStatuses = getAllowedStatuses(includeRc)
        return versions.chunked(20).flatMap {
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

    override fun getRelease(component: String, version: String, includeRc: Boolean) = try {
        val allowedStatuses = getAllowedStatuses(includeRc)
        val build = client.getBuild(component, version)
        if (allowedStatuses.contains(build.status)) ReleaseFullDTO(
            build.component,
            build.version,
            ComponentVersionStatus.valueOf(build.status.name),
            build.statusHistory[build.status],
            build.dependencies.map { DependencyDTO(it.component, it.version) }
        ) else throw IllegalVersionStatusException(
            "Build for version '$version' of component '$component' has status ${build.status}. Allowed statuses are $allowedStatuses"
        )
    } catch (_: RmNotFoundException) {
        throw NotFoundException("Build is not found for version '$version' of component '$component'")
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
    }
}
