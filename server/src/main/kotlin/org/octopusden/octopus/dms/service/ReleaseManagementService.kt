package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.dto.ComponentBuild

interface ReleaseManagementService {
    fun componentExists(component: String): Boolean
    fun validateVersionStatus(component: String, version: String, type: ArtifactType? = null)
    fun getComponentBuilds(
        component: String,
        buildStatuses: Array<BuildStatus>,
        versions: Set<String>
    ): List<ComponentBuild>
    fun getComponentBuild(component: String, version: String): ComponentBuild
}