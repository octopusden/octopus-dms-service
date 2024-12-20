package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "DOCKER artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"DOCKER\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"image\": \"test/test-component\",\n" +
            "  \"tag\": \"1.2.3\"\n" +
            "}"
)
class DockerArtifactDTO(
    id: Long,
    uploaded: Boolean,
    val image: String,
    val tag: String,
): ArtifactDTO(id, RepositoryType.DOCKER, uploaded) {
    override fun toString() = "DockerArtifactDTO(id=$id, uploaded=$uploaded, image=$image, tag=$tag)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DockerArtifactDTO
        if (id != other.id) return false
        if (uploaded != other.uploaded) return false
        if (image != other.image) return false
        if (tag != other.tag) return false
        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(id, uploaded, image, tag)
    }
}