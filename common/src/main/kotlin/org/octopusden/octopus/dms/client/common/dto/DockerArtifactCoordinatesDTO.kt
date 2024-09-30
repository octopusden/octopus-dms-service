package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Add DOCKER image details",
    example = "{\n" +
            "  \"repositoryType\": \"DOCKER\",\n" +
            "  \"image\": \"some-app/docker_image\",\n" +
            "}"
)
class DockerArtifactCoordinatesDTO(val image: String, val tag: String): ArtifactCoordinatesDTO(RepositoryType.DOCKER) {
    override fun toPath() = "$image/$tag"

    override fun toString() = "$image:$tag"
}