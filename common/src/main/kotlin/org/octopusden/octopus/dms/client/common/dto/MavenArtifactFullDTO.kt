package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Full MAVEN artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"MAVEN\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app.jar\",\n" +
            "  \"fileName\": \"some-app-1.2.3.jar\",\n" +
            "  \"gav\": {\n" +
            "    \"groupId\": \"domain.corp.distribution\",\n" +
            "    \"artifactId\": \"some-app\",\n" +
            "    \"version\": \"1.2.3\",\n" +
            "    \"packaging\": \"jar\",\n" +
            "    \"classifier\": null\n" +
            "  }\n" +
            "}"
)
class MavenArtifactFullDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    val gav: GavDTO
) : ArtifactFullDTO(id, RepositoryType.MAVEN, type, displayName, fileName) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MavenArtifactFullDTO) return false
        if (!super.equals(other)) return false
        if (gav != other.gav) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + gav.hashCode()
        return result
    }
}
