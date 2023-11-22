package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Add DEBIAN artifact details",
    example = "{\n" +
            "  \"repositoryType\": \"DEBIAN\",\n" +
            "  \"deb\": \"pool/s/some-app/some-app_0.1.2-3_amd64.deb\"\n" +
            "}"
)
class DebianArtifactCoordinatesDTO(val deb: String): ArtifactCoordinatesDTO(RepositoryType.DEBIAN) {
    override fun toPath() = deb

    override fun toString() = deb
}