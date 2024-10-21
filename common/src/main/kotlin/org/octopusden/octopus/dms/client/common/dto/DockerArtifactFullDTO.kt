package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "Full DOCKER artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"DOCKER\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"test/test-component\",\n" +
            "  \"fileName\": \"docker.io/test/test-component/1.2.3\"\n" +
            " \"tag\": \"1.2.3\",\n" +
            " \"image\": \"test/test-component\"\n" +
            "}"
)
class DockerArtifactFullDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    val image: String,
    val tag: String
) : ArtifactFullDTO(id, RepositoryType.DOCKER, type, displayName, fileName) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DockerArtifactFullDTO) return false
        if (!super.equals(other)) return false
        if (image != other.image) return false
        if (tag != other.tag) return false
        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), image, tag)
    }
}
