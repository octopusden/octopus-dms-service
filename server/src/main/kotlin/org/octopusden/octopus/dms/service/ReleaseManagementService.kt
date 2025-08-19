package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.dto.BuildDTO
import org.octopusden.octopus.dms.dto.BuildFullDTO

interface ReleaseManagementService {
    fun isComponentExists(component: String): Boolean
    fun findReleases(component: String, buildVersions: List<String>, includeRc: Boolean): List<BuildDTO>
    fun getRelease(component: String, version: String, includeRc: Boolean): BuildFullDTO
    fun findRelease(component: String, version: String, includeRc: Boolean): BuildFullDTO?
}