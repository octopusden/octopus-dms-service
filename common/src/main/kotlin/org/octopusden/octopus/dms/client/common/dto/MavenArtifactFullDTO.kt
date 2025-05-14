package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "Full MAVEN artifact info",
    example = "{\n" +
            "  \"id\": 1,\n" +
            "  \"repositoryType\": \"MAVEN\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app.jar\",\n" +
            "  \"fileName\": \"some-app-1.2.3.jar\",\n" +
            "  \"sha256\": \"9c540c6e28b3b7ed048934fb2f0ed0a319fccef7c424e0cab9b815637a67b590\",\n"+
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
    sha256: String,
    val gav: GavDTO
) : ArtifactFullDTO(id, RepositoryType.MAVEN, type, displayName, fileName, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MavenArtifactFullDTO

        return gav == other.gav
    }

    override fun hashCode() = Objects.hash(id, type, displayName, fileName, sha256, gav)

    override fun toString() = "MavenArtifactFullDTO(id=$id, type=$type, displayName='$displayName', fileName='$fileName', sha256='$sha256', gav=$gav)"
}
