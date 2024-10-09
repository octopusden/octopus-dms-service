package org.octopusden.octopus.dms.entity

import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactFullDTO
import java.util.regex.Pattern
import javax.persistence.AttributeConverter
import javax.persistence.Converter
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactFullDTO

@Entity
@Table(name = "component_version_artifact")
class ComponentVersionArtifact (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne
    val componentVersion: ComponentVersion,
    @ManyToOne
    val artifact: Artifact,
    val type: ArtifactType

) {
    val displayName get() = when (artifact.repositoryType) {
        RepositoryType.MAVEN -> {
            artifact as MavenArtifact
            artifact.artifactId + (if (artifact.version == componentVersion.version) "" else "-${artifact.version}") +
                    (artifact.classifier?.let { c -> "-$c." } ?: ".") + artifact.packaging
        }

        RepositoryType.DEBIAN -> {
            artifact.fileName.replace(Regex("_${Pattern.quote(componentVersion.version)}_"), "_")
        }

        RepositoryType.RPM -> {
            artifact.fileName.replace(Regex("-${Pattern.quote(componentVersion.version)}\\."), ".")
        }

        RepositoryType.DOCKER -> {
            artifact as DockerArtifact
            "${artifact.image}" + (if (artifact.tag == componentVersion.version) "" else ":${artifact.tag}")
        }
    }

    fun toShortDTO() = ArtifactShortDTO(artifact.id, artifact.repositoryType, type, displayName, artifact.fileName)

    fun toFullDTO() = when (artifact.repositoryType) {
        RepositoryType.MAVEN -> {
            artifact as MavenArtifact
            MavenArtifactFullDTO(artifact.id, type, displayName, artifact.fileName, artifact.gav)
        }

        RepositoryType.DEBIAN -> DebianArtifactFullDTO(artifact.id, type, displayName, artifact.fileName, artifact.path)

        RepositoryType.RPM -> RpmArtifactFullDTO(artifact.id, type, displayName, artifact.fileName, artifact.path)

        RepositoryType.DOCKER -> {
            artifact as DockerArtifact
            DockerArtifactFullDTO(artifact.id, type, displayName, artifact.fileName, artifact.image, artifact.tag)
        }
    }

    override fun toString(): String {
        return "ComponentVersionArtifact(id=$id, componentVersion='$componentVersion', type=$type, artifact=$artifact)"
    }
}

@Converter(autoApply = true)
class ArtifactTypeConverter: AttributeConverter<ArtifactType, String> {
    override fun convertToDatabaseColumn(attribute: ArtifactType?): String? {
        return attribute?.type
    }

    override fun convertToEntityAttribute(dbData: String?): ArtifactType? {
        if (dbData == null) {
            return null
        }
        return ArtifactType.findByType(dbData)
    }
}