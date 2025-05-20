package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "MAVEN artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"MAVEN\",\n" +
            "  \"uploaded\": false,\n" +
            "  \"sha256\": \"154cdd40a581ab4e904d74101c82de6292e78ab4b21597d245673bbc10a3732b\",\n"+
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
    sha256: String,
    val gav: GavDTO
): ArtifactDTO(id, RepositoryType.MAVEN, uploaded, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MavenArtifactDTO

        return gav == other.gav
    }

    override fun hashCode() = Objects.hash(id, uploaded, sha256, gav)

    override fun toString() = "MavenArtifactDTO(id=$id, uploaded=$uploaded, sha256='$sha256', gav=$gav)"
}