package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.DependencyDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.dto.ComponentVersionStatusWithInfoDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.entity.Artifact

interface ComponentService {
    fun getComponents(filter: ComponentRequestFilter? = null): List<ComponentDTO>
    fun getDependencies(componentName: String, version: String): List<DependencyDTO>
    fun deleteComponent(componentName: String, dryRun: Boolean)
    fun getComponentMinorVersions(componentName: String): Set<String>
    fun getComponentVersions(componentName: String, minorVersions: List<String>, includeRc: Boolean): List<ComponentVersionStatusWithInfoDTO>
    fun deleteComponentVersion(componentName: String, version: String, dryRun: Boolean)
    fun getPreviousLinesLatestVersions(componentName: String, version: String, includeRc: Boolean): List<String>
    fun getComponentVersionArtifacts(componentName: String, version: String, type: ArtifactType?): ArtifactsDTO
    fun getComponentVersionArtifact(componentName: String, version: String, artifactId: Long): ArtifactFullDTO
    fun downloadComponentVersionArtifact(componentName: String, version: String, artifactId: Long): DownloadArtifactDTO
    fun registerComponentVersionArtifact(componentName: String, version: String, artifactId: Long, failOnAlreadyExists: Boolean, registerArtifactDTO: RegisterArtifactDTO): ArtifactFullDTO
    fun deleteComponentVersionArtifact(componentName: String, version: String, artifactId: Long, dryRun: Boolean)
    fun deleteArtifact(artifact: Artifact)
}