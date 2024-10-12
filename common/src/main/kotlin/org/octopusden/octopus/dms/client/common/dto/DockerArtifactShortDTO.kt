package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Short artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"DOCKER\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app.jar\",\n" +
            "  \"fileName\": \"some-app-1.2.3.jar,\"\n" +
            "  \"imageName\": \"test/test-component\",\n" +
            "  \"tag\": \"1.2.3\"\n" +
            "}"
)
open class DockerArtifactShortDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    val imageName: String,
    val tag: String
): ArtifactShortDTO(id, RepositoryType.DOCKER, type, displayName, fileName) {
}