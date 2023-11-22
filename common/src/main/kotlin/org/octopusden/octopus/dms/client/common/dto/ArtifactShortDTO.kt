package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Short artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"MAVEN\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app.jar\",\n" +
            "  \"fileName\": \"some-app-1.2.3.jar\"\n" +
            "}"
)
open class ArtifactShortDTO(
    val id: Long,
    val repositoryType: RepositoryType,
    val type: ArtifactType,
    val displayName: String,
    val fileName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArtifactShortDTO) return false
        if (id != other.id) return false
        if (repositoryType != other.repositoryType) return false
        if (type != other.type) return false
        if (displayName != other.displayName) return false
        if (fileName != other.fileName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + repositoryType.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}
