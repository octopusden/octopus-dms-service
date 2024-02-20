package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.client.common.dto.ComponentDTO

interface AdminService {
    fun deleteInvalidComponents(dryRun: Boolean)
    fun deleteInvalidComponentsVersions(dryRun: Boolean)
    fun recalculateMinorVersions(dryRun: Boolean)
    fun deleteOrphanedArtifacts(dryRun: Boolean)
    fun renameComponent(name: String, newName: String, dryRun: Boolean): ComponentDTO
}