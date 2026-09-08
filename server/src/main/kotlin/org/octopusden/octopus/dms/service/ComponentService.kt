package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.PatchComponentVersionDTO
import org.octopusden.octopus.dms.dto.ComponentVersionWithInfoDTO

interface ComponentService {
    fun getComponents(filter: ComponentRequestFilter? = null): List<ComponentDTO>

    fun getComponentMinorVersions(componentName: String): Set<String>

    fun getComponentVersionsWithInfo(
        componentName: String,
        minorVersions: List<String>,
        includeRc: Boolean,
    ): List<ComponentVersionWithInfoDTO>

    fun getComponentVersionDependencies(
        componentName: String,
        version: String,
    ): List<ComponentVersionWithInfoDTO>

    fun patchComponentVersion(
        componentName: String,
        version: String,
        patchComponentVersionDTO: PatchComponentVersionDTO,
    ): ComponentVersionDTO

    fun getPreviousLinesLatestVersions(
        componentName: String,
        version: String,
        includeRc: Boolean,
    ): List<String>

    fun publishComponentVersion(
        componentName: String,
        version: String,
    ): ComponentVersionDTO

    fun revokeComponentVersion(
        componentName: String,
        version: String,
    ): ComponentVersionDTO
}
