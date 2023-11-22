package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.service.impl.ComponentBuild
import org.octopusden.octopus.dms.service.impl.VersionField
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.BuildStatus

interface RelengService {
    fun checkVersionStatus(component: String, version: String, type: ArtifactType? = null)
    fun getComponentBuilds(
        component: String,
        buildStatuses: Array<BuildStatus>,
        versions: Array<String>,
        versionsField: VersionField
    ): List<ComponentBuild>
}