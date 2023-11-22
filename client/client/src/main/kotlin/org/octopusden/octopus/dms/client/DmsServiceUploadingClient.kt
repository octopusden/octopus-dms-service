package org.octopusden.octopus.dms.client

import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactDTO
import java.io.InputStream

interface DmsServiceUploadingClient : DmsServiceFeignClient {
    fun uploadArtifact(
        artifactCoordinates: MavenArtifactCoordinatesDTO,
        file: InputStream,
        fileName: String? = null,
        failOnAlreadyExists: Boolean? = null
    ): MavenArtifactDTO
}
