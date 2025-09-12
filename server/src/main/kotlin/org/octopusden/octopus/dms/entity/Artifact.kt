package org.octopusden.octopus.dms.entity

import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactDTO
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
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
    val path: String,
    var sha256: String
) {
    val fileName get() = path.substringAfterLast("/")

    abstract val repositoryType: RepositoryType

    abstract fun toDTO(): ArtifactDTO

    override fun toString(): String {
        return "Artifact(id=$id, repositoryType=$repositoryType, uploaded=$uploaded, path=$path, sha256=$sha256)"
    }
}

@Entity
@DiscriminatorValue("DOCKER")
class DockerArtifact(
    uploaded: Boolean,
    path: String,
    sha256: String,
    val image: String,
    val tag: String,
) : Artifact(
    uploaded = uploaded,
    path = path,
    sha256 = sha256
) {
    override val repositoryType get() = RepositoryType.DOCKER

    override fun toDTO() = DockerArtifactDTO(id, uploaded, sha256, image, tag)

    fun imageIdentifier(dockerRegistry: String) = "$dockerRegistry/${this.image}:${this.tag}"
}

@Entity
@DiscriminatorValue("MAVEN")
class MavenArtifact(
    uploaded: Boolean,
    path: String,
    sha256: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val packaging: String,
    val classifier: String?
) : Artifact(
    uploaded = uploaded,
    path = path,
    sha256 = sha256
) {
    override val repositoryType get() = RepositoryType.MAVEN

    val gav get() = GavDTO(groupId, artifactId, version, packaging, classifier)

    override fun toDTO() = MavenArtifactDTO(id, uploaded, sha256, gav)
}

@Entity
@DiscriminatorValue("DEBIAN")
class DebianArtifact(
    uploaded: Boolean,
    path: String,
    sha256: String
) : Artifact(
    path = path,
    uploaded = uploaded,
    sha256 = sha256
) {
    override val repositoryType get() = RepositoryType.DEBIAN

    override fun toDTO() = DebianArtifactDTO(id, uploaded, sha256, path)
}

@Entity
@DiscriminatorValue("RPM")
class RpmArtifact(
    uploaded: Boolean,
    path: String,
    sha256: String
) : Artifact(
    path = path,
    uploaded = uploaded,
    sha256 = sha256
) {
    override val repositoryType get() = RepositoryType.RPM

    override fun toDTO() = RpmArtifactDTO(id, uploaded, sha256, path)
}