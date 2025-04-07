package org.octopusden.octopus.dms.repository

import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.entity.Artifact
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.octopusden.octopus.dms.exception.NotFoundException
import org.springframework.data.jpa.repository.JpaRepository

interface ComponentVersionArtifactRepository : JpaRepository<ComponentVersionArtifact, Long> {
    fun findByArtifact(artifact: Artifact): List<ComponentVersionArtifact>

    fun findByComponentVersion(componentVersion: ComponentVersion): List<ComponentVersionArtifact>

    fun findByComponentVersionAndType(
        componentVersion: ComponentVersion, type: ArtifactType
    ): List<ComponentVersionArtifact>

    fun findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
        componentVersionComponentName: String, componentVersionVersion: String, artifactId: Long
    ): ComponentVersionArtifact?

    fun findByComponentVersionAndArtifact(
        componentVersion: ComponentVersion, artifact: Artifact
    ): ComponentVersionArtifact?
}

fun ComponentVersionArtifactRepository.getByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
    componentVersionComponentName: String, componentVersionVersion: String, artifactId: Long
) = findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
    componentVersionComponentName, componentVersionVersion, artifactId
) ?: throw NotFoundException(
    "Artifact with ID '$artifactId' is not found for version '$componentVersionVersion' of component '$componentVersionComponentName'"
)