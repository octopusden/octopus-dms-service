package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.service.impl.dto.DownloadArtifactDTO
import org.springframework.web.multipart.MultipartFile

interface ArtifactService {
    fun repositories(repositoryType: RepositoryType): List<String>
    fun get(id: Long): ArtifactDTO
    fun find(artifactCoordinates: ArtifactCoordinatesDTO): ArtifactDTO
    fun download(id: Long): DownloadArtifactDTO
    fun add(failOnAlreadyExists: Boolean, artifactCoordinates: ArtifactCoordinatesDTO): ArtifactDTO
    fun upload(failOnAlreadyExists: Boolean, artifactCoordinates: ArtifactCoordinatesDTO, file: MultipartFile): ArtifactDTO
    fun delete(id: Long, dryRun: Boolean)
}