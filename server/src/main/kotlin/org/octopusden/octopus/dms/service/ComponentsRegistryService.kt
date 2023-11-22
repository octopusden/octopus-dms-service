package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.components.registry.core.dto.DetailedComponentVersion
import org.octopusden.releng.versions.VersionNames

interface ComponentsRegistryService {
    fun getComponents(): List<ComponentDTO>
    fun getComponentReadSecurityGroups(component: String): List<String>
    fun checkComponent(component: String)
    fun getDetailedComponentVersion(component: String, version: String): DetailedComponentVersion
    fun getVersionNames(): VersionNames
    fun findPreviousVersion(component: String, version: String, versions: List<String>): String
    fun findPreviousLines(component: String, version: String, versions: List<String>): List<String>
}
