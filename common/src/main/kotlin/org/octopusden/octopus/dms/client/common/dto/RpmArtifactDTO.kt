package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "RPM artifact info",
    example = "{\n" +
            "  \"id\": 3,\n" +
            "  \"repositoryType\": \"RPM\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"sha256\": \"d12b5effd1beb113ab3160d9eebb9f14c6080d3f97c863c2777050c5de314767\",\n"+
            "  \"rpm\": \"some-app/some-app-1.2.3-1.el8.x86_64.rpm\"\n" +
            "}"
)
class RpmArtifactDTO(
    id: Long,
    uploaded: Boolean,
    sha256: String,
    val rpm: String
): ArtifactDTO(id, RepositoryType.RPM, uploaded, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RpmArtifactDTO

        return rpm == other.rpm
    }

    override fun hashCode() = Objects.hash(id, uploaded, sha256, rpm)

    override fun toString() = "RpmArtifactDTO(id=$id, uploaded=$uploaded, sha256='$sha256', rpm=$rpm)"
}