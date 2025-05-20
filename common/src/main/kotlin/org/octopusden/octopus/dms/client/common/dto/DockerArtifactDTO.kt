package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "DOCKER artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"DOCKER\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"sha256\": \"6bfcad6118ab4edfcc55681e83451eec015f61b079522e1baa9c303c12311eda\",\n"+
            "  \"image\": \"test/test-component\",\n" +
            "  \"tag\": \"1.2.3\"\n" +
            "}"
)
class DockerArtifactDTO(
    id: Long,
    uploaded: Boolean,
    sha256: String,
    val image: String,
    val tag: String,
): ArtifactDTO(id, RepositoryType.DOCKER, uploaded, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as DockerArtifactDTO

        if (image != other.image) return false
        if (tag != other.tag) return false

        return true
    }

    override fun hashCode() = Objects.hash(id, uploaded, sha256, image, tag)

    override fun toString() = "DockerArtifactDTO(id=$id, uploaded=$uploaded, sha256='$sha256', image='$image', tag='$tag')"
}