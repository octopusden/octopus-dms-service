package org.octopusden.octopus.dms.repository

import org.octopusden.octopus.dms.entity.Artifact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ArtifactRepository : JpaRepository<Artifact, Long> {
    @Query(
        value = """
            SELECT *
            FROM artifact
            WHERE repository_type = :repositoryType AND path = :path
        """,
        nativeQuery = true,
    )
    fun findByRepositoryTypeAndPath(
        @Param("repositoryType") repositoryType: String,
        @Param("path") path: String,
    ): Artifact?
}
