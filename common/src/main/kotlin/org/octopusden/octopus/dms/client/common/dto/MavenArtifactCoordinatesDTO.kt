package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Add MAVEN artifact details",
    example = "{\n" +
            "  \"repositoryType\": \"MAVEN\",\n" +
            "  \"gav\": {\n" +
            "    \"groupId\": \"domain.corp.distribution\",\n" +
            "    \"artifactId\": \"some-app\",\n" +
            "    \"version\": \"1.2.3\",\n" +
            "    \"packaging\": \"jar\",\n" +
            "    \"classifier\": null\n" +
            "  }\n" +
            "}"
)
class MavenArtifactCoordinatesDTO(val gav: GavDTO): ArtifactCoordinatesDTO(RepositoryType.MAVEN) {
    override fun toPath() = gav.toPath()

    override fun toString() = gav.toString()
}