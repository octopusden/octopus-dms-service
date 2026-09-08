package org.octopusden.octopus.dms.client

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import java.io.InputStream

interface DmsServiceUploadingClient : DmsServiceFeignClient {
    fun uploadArtifact(
        artifactCoordinates: ArtifactCoordinatesDTO,
        file: InputStream,
        fileName: String? = null,
        failOnAlreadyExists: Boolean? = null,
    ): ArtifactDTO

    fun uploadAndRegisterComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactCoordinates: ArtifactCoordinatesDTO,
        file: InputStream,
        fileName: String? = null,
        artifactType: ArtifactType,
        failOnAlreadyExists: Boolean? = null,
    ): ArtifactFullDTO
}
