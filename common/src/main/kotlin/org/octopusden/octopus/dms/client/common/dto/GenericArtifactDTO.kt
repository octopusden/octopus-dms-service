package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "GENERIC artifact info",
    example = "{\n" +
        "  \"id\": 2,\n" +
        "  \"repositoryType\": \"GENERIC\",\n" +
        "  \"uploaded\": false,\n" +
        "  \"sha256\": \"f61bb90bbf0c5cb9dc59e902f8f19f348d045c2ed1abc2528b84ed1fae6dc9af\",\n" +
        "  \"generic\": \"path/1.0.0/some-data.tgz\"\n" +
        "}",
)
class GenericArtifactDTO(
    id: Long,
    uploaded: Boolean,
    sha256: String,
    val generic: String,
) : ArtifactDTO(id, RepositoryType.GENERIC, uploaded, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as GenericArtifactDTO

        return generic == other.generic
    }

    override fun hashCode() = Objects.hash(id, uploaded, sha256, generic)

    override fun toString() = "GenericArtifactDTO(id=$id, uploaded=$uploaded, sha256='$sha256', generic='$generic')"
}
