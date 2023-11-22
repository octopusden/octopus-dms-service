package org.octopusden.octopus.dms.repository

import org.octopusden.octopus.dms.entity.Component
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ComponentRepository : JpaRepository<Component, Long> {
    fun findByName(name: String): Component?

    @Query(nativeQuery = true, value = "SELECT 1 FROM pg_advisory_xact_lock(:hash, 0)")
    fun lock(@Param("hash") hash: Int)
}
