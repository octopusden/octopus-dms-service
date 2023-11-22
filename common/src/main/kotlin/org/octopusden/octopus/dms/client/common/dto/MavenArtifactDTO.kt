package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "MAVEN artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"MAVEN\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"gav\": {\n" +
            "    \"groupId\": \"domain.corp.distribution\",\n" +
            "    \"artifactId\": \"some-app\",\n" +
            "    \"version\": \"1.2.3\",\n" +
            "    \"packaging\": \"jar\",\n" +
            "    \"classifier\": null\n" +
            "  }\n" +
            "}"
)
class MavenArtifactDTO(
    id: Long,
    uploaded: Boolean,
    val gav: GavDTO
): ArtifactDTO(id, RepositoryType.MAVEN, uploaded) {
    override fun toString() = "MavenArtifactDTO(id=$id, uploaded=$uploaded, gav=$gav)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MavenArtifactDTO
        if (id != other.id) return false
        if (uploaded != other.uploaded) return false
        if (gav != other.gav) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uploaded.hashCode()
        result = 31 * result + gav.hashCode()
        return result
    }
}