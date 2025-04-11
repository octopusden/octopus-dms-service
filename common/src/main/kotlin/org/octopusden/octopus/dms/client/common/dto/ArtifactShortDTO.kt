package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

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
        if (javaClass != other?.javaClass) return false

        other as ArtifactShortDTO

        if (id != other.id) return false
        if (repositoryType != other.repositoryType) return false
        if (type != other.type) return false
        if (displayName != other.displayName) return false
        if (fileName != other.fileName) return false

        return true
    }

    override fun hashCode() = Objects.hash(id, repositoryType, type, displayName, fileName)

    override fun toString() = "ArtifactShortDTO(id=$id, repositoryType=$repositoryType, type=$type, displayName='$displayName', fileName='$fileName')"
}
