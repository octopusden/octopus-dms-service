package org.octopusden.octopus.dms.repository

import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ComponentVersionRepository : JpaRepository<ComponentVersion, Long> {
    @Query("select distinct cv.minorVersion from ComponentVersion cv where cv.component.name = :componentName")
    fun getMinorVersionsByComponentName(@Param("componentName") componentName: String): Set<String>

    @Query("select cv from ComponentVersion cv where cv.component.name = :componentName and cv.minorVersion in :minorVersions")
    fun findByComponentNameAndMinorVersions(
        @Param("componentName") componentName: String,
        @Param("minorVersions") minorVersions: List<String>
    ): List<ComponentVersion>

    fun findByComponent(component: Component): List<ComponentVersion>

    fun findByComponentName(componentName: String): List<ComponentVersion>

    fun findByComponentAndVersion(component: Component, version: String): ComponentVersion?

    fun findByComponentNameAndVersion(componentName: String, version: String): ComponentVersion?
}
