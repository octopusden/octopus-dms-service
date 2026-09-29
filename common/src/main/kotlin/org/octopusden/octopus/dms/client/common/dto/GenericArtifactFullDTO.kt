package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "Full GENERIC artifact info",
    example = "{\n" +
        "  \"id\": 2,\n" +
        "  \"repositoryType\": \"GENERIC\",\n" +
        "  \"type\": \"distribution\",\n" +
        "  \"displayName\": \"some-data.tgz\",\n" +
        "  \"fileName\": \"some-data.tgz\",\n" +
        "  \"sha256\": \"8247830a7fae3307ac34d6d9b8426d31568f7774bc0085bbe5ba1e4534ba8337\",\n" +
        "  \"generic\": \"path/1.0.0/some-data.tgz\"\n" +
        "}",
)
class GenericArtifactFullDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    sha256: String,
    val generic: String,
) : ArtifactFullDTO(id, RepositoryType.GENERIC, type, displayName, fileName, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as GenericArtifactFullDTO

        return generic == other.generic
    }

    override fun hashCode() = Objects.hash(id, type, displayName, fileName, sha256, generic)

    override fun toString() =
        "GenericArtifactFullDTO(id=$id, type=$type, displayName='$displayName', fileName='$fileName', sha256='$sha256', generic='$generic')"
}
