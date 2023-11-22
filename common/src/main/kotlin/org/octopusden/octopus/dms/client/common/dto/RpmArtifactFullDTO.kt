package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Full RPM artifact info",
    example = "{\n" +
            "  \"id\": 3,\n" +
            "  \"repositoryType\": \"RPM\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app.el8.x86_64.rpm\",\n" +
            "  \"fileName\": \"some-app-1.2.3-1.el8.x86_64.rpm\",\n" +
            "  \"rpm\": \"some-app/some-app-1.2.3-1.el8.x86_64.rpm\"\n" +
            "}"
)
class RpmArtifactFullDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    val rpm: String
) : ArtifactFullDTO(id, RepositoryType.RPM, type, displayName, fileName) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RpmArtifactFullDTO) return false
        if (!super.equals(other)) return false
        if (rpm != other.rpm) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + rpm.hashCode()
        return result
    }
}
