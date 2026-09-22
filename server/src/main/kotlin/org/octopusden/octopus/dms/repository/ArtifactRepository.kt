package org.octopusden.octopus.dms.repository

import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.entity.Artifact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

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
        repositoryType: String,
        path: String,
    ): Artifact?

    fun findByRepositoryTypeAndPath(
        repositoryType: RepositoryType,
        path: String,
    ): Artifact? = findByRepositoryTypeAndPath(repositoryType.name, path)
}
