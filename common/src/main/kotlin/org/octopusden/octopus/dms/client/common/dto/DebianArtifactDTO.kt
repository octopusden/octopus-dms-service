package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "DEBIAN artifact info",
    example = "{\n" +
            "  \"id\": 2,\n" +
            "  \"repositoryType\": \"DEBIAN\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"sha256\": \"f61bb90bbf0c5cb9dc59e902f8f19f348d045c2ed1abc2528b84ed1fae6dc9af\",\n"+
            "  \"deb\": \"pool/s/some-app/some-app_0.1.2-3_amd64.deb\"\n" +
            "}"
)
class DebianArtifactDTO(
    id: Long,
    uploaded: Boolean,
    sha256: String,
    val deb: String
): ArtifactDTO(id, RepositoryType.DEBIAN, uploaded, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as DebianArtifactDTO

        return deb == other.deb
    }

    override fun hashCode() = Objects.hash(id, uploaded, sha256, deb)

    override fun toString() = "DebianArtifactDTO(id=$id, uploaded=$uploaded, sha256='$sha256', deb='$deb')"
}