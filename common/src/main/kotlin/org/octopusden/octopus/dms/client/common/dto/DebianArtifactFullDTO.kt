package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Full DEBIAN artifact info",
    example = "{\n" +
            "  \"id\": 2,\n" +
            "  \"repositoryType\": \"DEBIAN\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app_amd64.deb\",\n" +
            "  \"fileName\": \"some-app_0.1.2-3_amd64.deb\",\n" +
            "  \"deb\": \"pool/s/some-app/some-app_0.1.2-3_amd64.deb\"\n" +
            "}"
)
class DebianArtifactFullDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    val deb: String
) : ArtifactFullDTO(id, RepositoryType.DEBIAN, type, displayName, fileName) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DebianArtifactFullDTO) return false
        if (!super.equals(other)) return false
        if (deb != other.deb) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + deb.hashCode()
        return result
    }
}