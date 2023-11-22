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
    JsonSubTypes.Type(MavenArtifactDTO::class, name = "MAVEN"),
    JsonSubTypes.Type(DebianArtifactDTO::class, name = "DEBIAN"),
    JsonSubTypes.Type(RpmArtifactDTO::class, name = "RPM")
)
@Schema(
    description = "Artifact info",
    discriminatorProperty = "repositoryType",
    discriminatorMapping = [
        DiscriminatorMapping("MAVEN", schema = MavenArtifactDTO::class),
        DiscriminatorMapping("DEBIAN", schema = DebianArtifactDTO::class),
        DiscriminatorMapping("RPM", schema = RpmArtifactDTO::class)
    ]
)
abstract class ArtifactDTO(
    val id: Long,
    val repositoryType: RepositoryType,
    val uploaded: Boolean
)