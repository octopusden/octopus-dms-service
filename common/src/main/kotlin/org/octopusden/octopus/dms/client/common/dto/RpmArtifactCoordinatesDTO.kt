package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Add RPM artifact details",
    example = "{\n" +
            "  \"repositoryType\": \"RPM\",\n" +
            "  \"rpm\": \"some-app/some-app-1.2.3-1.el8.x86_64.rpm\"\n" +
            "}"
)
class RpmArtifactCoordinatesDTO(val rpm: String): ArtifactCoordinatesDTO(RepositoryType.RPM) {
    override fun toPath() = rpm

    override fun toString() = "RpmArtifactCoordinatesDTO(rpm='$rpm')"
}