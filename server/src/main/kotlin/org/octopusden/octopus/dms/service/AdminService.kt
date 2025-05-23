package org.octopusden.octopus.dms.service

interface AdminService {
    fun deleteInvalidComponents(dryRun: Boolean)
    fun deleteInvalidComponentsVersions(dryRun: Boolean)
    fun deleteInvalidArtifacts(updateSha256: Boolean, dryRun: Boolean)
    fun deleteOrphanedArtifacts(dryRun: Boolean)
    fun renameComponent(name: String, newName: String, dryRun: Boolean)
}