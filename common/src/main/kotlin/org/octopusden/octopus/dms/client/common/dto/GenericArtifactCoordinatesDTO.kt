package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Add GENERIC artifact details",
    example = "{\n" +
        "  \"repositoryType\": \"GENERIC\",\n" +
        "  \"generic\": \"path/1.0.0/some-data.tgz\"\n" +
        "}",
)
class GenericArtifactCoordinatesDTO(
    val generic: String,
) : ArtifactCoordinatesDTO(RepositoryType.GENERIC) {
    override fun toPath() = generic

    override fun toString() = "GenericArtifactCoordinatesDTO(generic='$generic')"
}
