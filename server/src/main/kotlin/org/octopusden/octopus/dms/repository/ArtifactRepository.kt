package org.octopusden.octopus.dms.repository

import org.octopusden.octopus.dms.entity.Artifact
import org.springframework.data.jpa.repository.JpaRepository

interface ArtifactRepository : JpaRepository<Artifact, Long> {
    fun findByPath(path: String): Artifact?
}
