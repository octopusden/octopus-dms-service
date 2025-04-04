package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.dto.ReleaseDTO
import org.octopusden.octopus.dms.dto.ReleaseFullDTO

interface ReleaseManagementService {
    fun isComponentExists(component: String): Boolean
    fun findReleases(component: String, versions: List<String>, includeRc: Boolean): List<ReleaseDTO>
    fun getRelease(component: String, version: String, includeRc: Boolean): ReleaseFullDTO
    fun findRelease(component: String, version: String, includeRc: Boolean): ReleaseFullDTO?
}