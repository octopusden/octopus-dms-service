package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.springframework.web.multipart.MultipartFile

interface ComponentVersionArtifactService {

    fun getComponentVersionArtifacts(
        componentName: String,
        version: String,
        type: ArtifactType?,
    ): ArtifactsDTO

    fun getComponentVersionArtifactFullDTOs(
        componentVersion: ComponentVersion,
    ): List<ArtifactFullDTO>

    fun getComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
    ): ArtifactFullDTO

    fun downloadComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
    ): DownloadArtifactDTO

    fun registerComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
        failOnAlreadyExists: Boolean,
        registerArtifactDTO: RegisterArtifactDTO,
    ): ArtifactFullDTO

    fun deleteComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
        dryRun: Boolean,
    )

    fun uploadAndRegisterComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactCoordinates: ArtifactCoordinatesDTO,
        file: MultipartFile,
        artifactType: ArtifactType,
        failOnAlreadyExists: Boolean
    ): ArtifactFullDTO

    fun addAndRegisterComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactCoordinates: ArtifactCoordinatesDTO,
        artifactType: ArtifactType,
        failOnAlreadyExists: Boolean
    ): ArtifactFullDTO
}