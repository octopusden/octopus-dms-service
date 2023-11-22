package org.octopusden.octopus.dms.repository

import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.entity.Artifact
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.springframework.data.jpa.repository.JpaRepository

interface ComponentVersionArtifactRepository : JpaRepository<ComponentVersionArtifact, Long> {
    fun findByComponentVersionComponentNameAndComponentVersionVersion(
        componentVersionComponentName: String,
        componentVersionVersion: String
    ): List<ComponentVersionArtifact>

    fun findByComponentVersionComponentNameAndComponentVersionVersionAndType(
        componentVersionComponentName: String,
        componentVersionVersion: String,
        type: ArtifactType
    ): List<ComponentVersionArtifact>

    fun findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
        componentVersionComponentName: String,
        componentVersionVersion: String,
        artifactId: Long
    ): ComponentVersionArtifact?

    fun findByComponentVersionAndArtifact(
        componentVersion: ComponentVersion,
        artifact: Artifact
    ): ComponentVersionArtifact?
}