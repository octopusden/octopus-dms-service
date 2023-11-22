package org.octopusden.octopus.dms.service

interface AdminService {
    fun deleteInvalidComponents(dryRun: Boolean)
    fun deleteInvalidComponentsVersions(dryRun: Boolean)
    fun recalculateMinorVersions(dryRun: Boolean)
    fun deleteOrphanedArtifacts(dryRun: Boolean)
}