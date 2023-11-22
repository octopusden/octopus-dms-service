package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "RPM artifact info",
    example = "{\n" +
            "  \"id\": 3,\n" +
            "  \"repositoryType\": \"RPM\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"rpm\": \"some-app/some-app-1.2.3-1.el8.x86_64.rpm\"\n" +
            "}"
)
class RpmArtifactDTO(
    id: Long,
    uploaded: Boolean,
    val rpm: String
): ArtifactDTO(id, RepositoryType.RPM, uploaded) {
    override fun toString() = "RpmArtifactDTO(id=$id, uploaded=$uploaded, rpm=$rpm)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RpmArtifactDTO
        if (id != other.id) return false
        if (uploaded != other.uploaded) return false
        if (rpm != other.rpm) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uploaded.hashCode()
        result = 31 * result + rpm.hashCode()
        return result
    }
}