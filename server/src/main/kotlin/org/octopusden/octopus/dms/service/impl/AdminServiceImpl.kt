package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.service.AdminService
import org.octopusden.octopus.dms.service.ArtifactService
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = false)
class AdminServiceImpl( //TODO: move functionality to ComponentService and ArtifactService?
    private val componentService: ComponentService,
    private val artifactService: ArtifactService,
    private val componentsRegistryService: ComponentsRegistryService,
    private val storageService: StorageService,
    private val componentRepository: ComponentRepository,
    private val componentVersionRepository: ComponentVersionRepository,
    private val artifactRepository: ArtifactRepository
) : AdminService {

    override fun deleteInvalidComponents(dryRun: Boolean) {
        log.info("Delete invalid components")
        val validComponents = componentService.getComponents().map { it.name }
        return componentRepository.findAll().filter {
            !validComponents.contains(it.name)
        }.forEach { componentService.deleteComponent(it.name, dryRun) }
    }

    override fun deleteInvalidComponentsVersions(dryRun: Boolean) {
        log.info("Delete invalid components versions")
        componentService.getComponents().forEach {
            val validComponentVersions = componentService.getComponentVersions(it.name, emptyList(), true).map { v -> v.version }
            componentVersionRepository.findByComponentName(it.name).filter { v ->
                !validComponentVersions.contains(v.version)
            }.forEach { v -> componentService.deleteComponentVersion(v.component.name, v.version, dryRun) }
        }
    }

    override fun recalculateMinorVersions(dryRun: Boolean) {
        log.info("Recalculate minor versions")
        componentVersionRepository.findAll().forEach {
            val detailedComponentVersion = componentsRegistryService.getDetailedComponentVersion(it.component.name, it.version)
            if (it.minorVersion != detailedComponentVersion.minorVersion.version) {
                if (!dryRun) {
                    it.minorVersion = detailedComponentVersion.minorVersion.version
                    componentVersionRepository.save(it)
                }
                log.info("Updated minor version for version '${it.version}' of component '${it.component.name}' from '${it.minorVersion}' to '${detailedComponentVersion.minorVersion.version}'")
            }
        }
    }

    override fun deleteOrphanedArtifacts(dryRun: Boolean) {
        log.info("Delete orphaned artifacts")
        artifactRepository.findAll().forEach {
            try {
                storageService.find(it, true)
            } catch (e: UnableToFindArtifactException) {
                artifactService.delete(it.id, dryRun)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminServiceImpl::class.java)
    }
}