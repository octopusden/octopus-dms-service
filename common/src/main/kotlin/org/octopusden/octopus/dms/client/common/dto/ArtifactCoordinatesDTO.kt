package org.octopusden.octopus.dms.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "repositoryType",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(MavenArtifactCoordinatesDTO::class, name = "MAVEN"),
    JsonSubTypes.Type(DebianArtifactCoordinatesDTO::class, name = "DEBIAN"),
    JsonSubTypes.Type(RpmArtifactCoordinatesDTO::class, name = "RPM"),
    JsonSubTypes.Type(DockerArtifactCoordinatesDTO::class, name = "DOCKER")
)
@Schema(
    description = "Artifact coordinates",
    discriminatorProperty = "repositoryType",
    discriminatorMapping = [
        DiscriminatorMapping("MAVEN", schema = MavenArtifactCoordinatesDTO::class),
        DiscriminatorMapping("DEBIAN", schema = DebianArtifactCoordinatesDTO::class),
        DiscriminatorMapping("RPM", schema = RpmArtifactCoordinatesDTO::class),
        DiscriminatorMapping("DOCKER", schema = DockerArtifactCoordinatesDTO::class)
    ]
)
abstract class ArtifactCoordinatesDTO(
    val repositoryType: RepositoryType
) {
    abstract fun toPath(): String
}