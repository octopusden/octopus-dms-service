package org.octopusden.octopus.dms.client

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import java.io.InputStream

interface DmsServiceUploadingClient : DmsServiceFeignClient {
    fun uploadArtifact(
        artifactCoordinates: ArtifactCoordinatesDTO,
        file: InputStream,
        fileName: String? = null,
        failOnAlreadyExists: Boolean? = null,
    ): ArtifactDTO
}
