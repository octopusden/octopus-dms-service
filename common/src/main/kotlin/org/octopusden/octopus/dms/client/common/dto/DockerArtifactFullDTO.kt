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
            "  \"fileName\": \"docker.io/test/test-component:1.2.3\"\n" +
            "  \"tag\": \"1.2.3\",\n" +
            "  \"image\": \"test/test-component\"\n" +
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
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as DockerArtifactFullDTO

        if (image != other.image) return false
        if (tag != other.tag) return false

        return true
    }

    override fun hashCode() = Objects.hash(id, type, displayName, fileName, image, tag)

    override fun toString() = "DockerArtifactFullDTO(id=$id, type=$type, displayName='$displayName', fileName='$fileName', image='$image', tag='$tag')"
}
