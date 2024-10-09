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
    JsonSubTypes.Type(MavenArtifactFullDTO::class, name = "MAVEN"),
    JsonSubTypes.Type(DebianArtifactFullDTO::class, name = "DEBIAN"),
    JsonSubTypes.Type(RpmArtifactFullDTO::class, name = "RPM"),
    JsonSubTypes.Type(DockerArtifactFullDTO::class, name = "DOCKER")
)
@Schema(
    description = "Full artifact info",
    discriminatorProperty = "repositoryType",
    discriminatorMapping = [
        DiscriminatorMapping("MAVEN", schema = MavenArtifactFullDTO::class),
        DiscriminatorMapping("DEBIAN", schema = DebianArtifactFullDTO::class),
        DiscriminatorMapping("RPM", schema = RpmArtifactFullDTO::class),
        DiscriminatorMapping("DOCKER", schema = DockerArtifactFullDTO::class)
    ]
)
abstract class ArtifactFullDTO(
    id: Long,
    repositoryType: RepositoryType,
    type: ArtifactType,
    displayName: String,
    fileName: String
): ArtifactShortDTO(id, repositoryType, type, displayName, fileName)
