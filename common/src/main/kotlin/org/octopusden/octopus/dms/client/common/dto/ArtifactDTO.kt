package org.octopusden.octopus.dms.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

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
    JsonSubTypes.Type(RpmArtifactDTO::class, name = "RPM"),
    JsonSubTypes.Type(DockerArtifactDTO::class, name = "DOCKER")
)
@Schema(
    description = "Artifact info",
    discriminatorProperty = "repositoryType",
    discriminatorMapping = [
        DiscriminatorMapping("MAVEN", schema = MavenArtifactDTO::class),
        DiscriminatorMapping("DEBIAN", schema = DebianArtifactDTO::class),
        DiscriminatorMapping("RPM", schema = RpmArtifactDTO::class),
        DiscriminatorMapping("DOCKER", schema = DockerArtifactDTO::class)
    ]
)
abstract class ArtifactDTO(
    val id: Long,
    val repositoryType: RepositoryType,
    val uploaded: Boolean,
    val sha256: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArtifactDTO

        if (id != other.id) return false
        if (repositoryType != other.repositoryType) return false
        if (uploaded != other.uploaded) return false
        if (sha256 != other.sha256) return false

        return true
    }

    override fun hashCode() = Objects.hash(id, repositoryType, uploaded, sha256)
}