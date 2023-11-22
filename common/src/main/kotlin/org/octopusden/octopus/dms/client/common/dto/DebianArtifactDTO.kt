package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "DEBIAN artifact info",
    example = "{\n" +
            "  \"id\": 2,\n" +
            "  \"repositoryType\": \"DEBIAN\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"deb\": \"pool/s/some-app/some-app_0.1.2-3_amd64.deb\"\n" +
            "}"
)
class DebianArtifactDTO(
    id: Long,
    uploaded: Boolean,
    val deb: String
): ArtifactDTO(id, RepositoryType.DEBIAN, uploaded) {
    override fun toString() = "MavenArtifactDTO(id=$id, uploaded=$uploaded, deb=$deb)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DebianArtifactDTO
        if (id != other.id) return false
        if (uploaded != other.uploaded) return false
        if (deb != other.deb) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uploaded.hashCode()
        result = 31 * result + deb.hashCode()
        return result
    }
}