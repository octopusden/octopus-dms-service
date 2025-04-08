package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.PatchComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.dto.ComponentVersionWithInfoDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO

interface ComponentService {
    fun getComponents(filter: ComponentRequestFilter? = null): List<ComponentDTO>
    fun deleteComponent(componentName: String, dryRun: Boolean)
    fun getComponentMinorVersions(componentName: String): Set<String>
    fun getComponentVersionsWithInfo(componentName: String, minorVersions: List<String>, includeRc: Boolean): List<ComponentVersionWithInfoDTO>
    fun getComponentVersionDependencies(componentName: String, version: String): List<ComponentVersionWithInfoDTO>
    fun deleteComponentVersion(componentName: String, version: String, dryRun: Boolean)
    fun patchComponentVersion(componentName: String, version: String, patchComponentVersionDTO: PatchComponentVersionDTO): ComponentVersionDTO
    fun getPreviousLinesLatestVersions(componentName: String, version: String, includeRc: Boolean): List<String>
    fun getComponentVersionArtifacts(componentName: String, version: String, type: ArtifactType?): ArtifactsDTO
    fun getComponentVersionArtifact(componentName: String, version: String, artifactId: Long): ArtifactFullDTO
    fun downloadComponentVersionArtifact(componentName: String, version: String, artifactId: Long): DownloadArtifactDTO
    fun registerComponentVersionArtifact(componentName: String, version: String, artifactId: Long, failOnAlreadyExists: Boolean, registerArtifactDTO: RegisterArtifactDTO): ArtifactFullDTO
    fun deleteComponentVersionArtifact(componentName: String, version: String, artifactId: Long, dryRun: Boolean)
}