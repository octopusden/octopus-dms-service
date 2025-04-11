package org.octopusden.octopus.dms.entity

import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactDTO
import javax.persistence.DiscriminatorColumn
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Inheritance
import javax.persistence.InheritanceType
import javax.persistence.Table
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactDTO

@Entity
@Table(name = "artifact")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "repository_type")
abstract class Artifact(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val uploaded: Boolean,
    val path: String
) {
    val fileName get() = path.substringAfterLast("/")

    abstract val repositoryType: RepositoryType

    abstract fun toDTO(): ArtifactDTO

    override fun toString(): String {
        return "Artifact(id=$id, repositoryType=$repositoryType, uploaded=$uploaded, path=$path)"
    }
}

@Entity
@DiscriminatorValue("DOCKER")
class DockerArtifact(
    uploaded: Boolean,
    path: String,
    val image: String,
    val tag: String,
) : Artifact(
    uploaded = uploaded,
    path = path
) {
    override val repositoryType get() = RepositoryType.DOCKER

    override fun toDTO() = DockerArtifactDTO(id, uploaded, image, tag)

    fun imageIdentifier(dockerRegistry: String) = "$dockerRegistry/${this.image}:${this.tag}"
}

@Entity
@DiscriminatorValue("MAVEN")
class MavenArtifact(
    uploaded: Boolean,
    path: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val packaging: String,
    val classifier: String?
) : Artifact(
    uploaded = uploaded,
    path = path
) {
    override val repositoryType get() = RepositoryType.MAVEN

    val gav get() = GavDTO(groupId, artifactId, version, packaging, classifier)

    override fun toDTO() = MavenArtifactDTO(id, uploaded, gav)
}

@Entity
@DiscriminatorValue("DEBIAN")
class DebianArtifact(
    uploaded: Boolean,
    path: String
) : Artifact(
    path = path,
    uploaded = uploaded
) {
    override val repositoryType get() = RepositoryType.DEBIAN

    override fun toDTO() = DebianArtifactDTO(id, uploaded, path)
}

@Entity
@DiscriminatorValue("RPM")
class RpmArtifact(
    uploaded: Boolean,
    path: String
) : Artifact(
    path = path,
    uploaded = uploaded
) {
    override val repositoryType get() = RepositoryType.RPM

    override fun toDTO() = RpmArtifactDTO(id, uploaded, path)
}