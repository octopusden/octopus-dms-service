package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as DebianArtifactDTO

        return deb == other.deb
    }

    override fun hashCode() = Objects.hash(id, uploaded, deb)

    override fun toString() = "DebianArtifactDTO(id=$id, uploaded=$uploaded, deb='$deb')"
}