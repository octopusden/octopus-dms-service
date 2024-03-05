package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.components.registry.core.dto.DetailedComponentVersion
import org.octopusden.releng.versions.VersionNames

interface ComponentsRegistryService {
    /**
     * Get component by name
     * @param name component name
     * @return component
     * @throws NotFoundException if component not found
     */
    fun getComponent(name: String): ComponentDTO
    fun getExplicitExternalComponents(): List<ComponentDTO>
    fun checkComponent(component: String)
    fun getDetailedComponentVersion(component: String, version: String): DetailedComponentVersion
    fun getVersionNames(): VersionNames
    fun findPreviousVersion(component: String, version: String, versions: List<String>): String
    fun findPreviousLines(component: String, version: String, versions: List<String>): List<String>
}
