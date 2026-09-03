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
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(MavenArtifactCoordinatesDTO::class, name = "MAVEN"),
    JsonSubTypes.Type(DebianArtifactCoordinatesDTO::class, name = "DEBIAN"),
    JsonSubTypes.Type(RpmArtifactCoordinatesDTO::class, name = "RPM"),
    JsonSubTypes.Type(DockerArtifactCoordinatesDTO::class, name = "DOCKER"),
)
@Schema(
    description = "Artifact coordinates",
    oneOf = [
        MavenArtifactCoordinatesDTO::class,
        DebianArtifactCoordinatesDTO::class,
        RpmArtifactCoordinatesDTO::class,
        DockerArtifactCoordinatesDTO::class,
    ],
    discriminatorProperty = "repositoryType",
    discriminatorMapping = [
        DiscriminatorMapping(
            value = "MAVEN",
            schema = MavenArtifactCoordinatesDTO::class,
        ),
        DiscriminatorMapping(
            value = "DEBIAN",
            schema = DebianArtifactCoordinatesDTO::class,
        ),
        DiscriminatorMapping(
            value = "RPM",
            schema = RpmArtifactCoordinatesDTO::class,
        ),
        DiscriminatorMapping(
            value = "DOCKER",
            schema = DockerArtifactCoordinatesDTO::class,
        ),
    ],
)
abstract class ArtifactCoordinatesDTO(
    val repositoryType: RepositoryType,
) {
    abstract fun toPath(): String
}
