package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.exception.IllegalComponentRenamingException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.service.AdminService
import org.octopusden.octopus.dms.service.ArtifactService
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.dms.service.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException as ComponentsRegistryNotFoundException

@Service
@Transactional(readOnly = false)
class AdminServiceImpl( //TODO: move functionality to ComponentService and ArtifactService?
    private val componentService: ComponentService,
    private val artifactService: ArtifactService,
    private val componentsRegistryService: ComponentsRegistryService,
    private val storageService: StorageService,
    private val componentRepository: ComponentRepository,
    private val componentVersionRepository: ComponentVersionRepository,
    private val artifactRepository: ArtifactRepository,
    private val relengService: ReleaseManagementService
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
            val validComponentVersions = componentService.getComponentVersionsWithInfo(it.name, emptyList(), true)
                .map { v -> v.version.version }.toSet()
            componentVersionRepository.findByComponentName(it.name).filter { v ->
                !validComponentVersions.contains(v.version)
            }.forEach { v -> componentService.deleteComponentVersion(v.component.name, v.version, dryRun) }
        }
    }

    override fun recalculateMinorVersions(dryRun: Boolean) {
        log.info("Recalculate minor versions")
        componentVersionRepository.findAll().forEach {
            val detailedComponentVersion =
                componentsRegistryService.getDetailedComponentVersion(it.component.name, it.version)
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
            } catch (_: UnableToFindArtifactException) {
                artifactService.delete(it.id, dryRun)
            }
        }
    }

    /**
     * Update component name and return new component name.
     * By this point, the component should have already been renamed in 'releng'.
     * @param name - old component name
     * @param newName - new component name
     * @param dryRun - if true, do not update component name
     * @throws NotFoundException if component with name [name] not found in releng
     */
    @Transactional(readOnly = false)
    override fun renameComponent(name: String, newName: String, dryRun: Boolean) {
        log.info("Update component name from '$name' to '$newName'")

        if (isComponentPresentInRegistry(name)) {
            log.error("Component with name $name exists in components registry")
            throw IllegalComponentRenamingException("Component with name $name exists in components registry")
        }
        if (!isComponentPresentInRegistry(newName)) {
            log.error("Component with name $newName not found in components registry")
            throw NotFoundException("Component with name $newName not found in components registry")
        }
        if (!relengService.isComponentExists(newName)) {
            throw NotFoundException("Component with name $newName not found in releng")
        }
        val component = componentRepository.findByName(newName)
        val existedComponent = componentRepository.findByName(name)

        if (existedComponent != null && component != null) {
            with("Both component with name $name and name $newName exists in DMS") {
                log.error(this)
                throw IllegalComponentRenamingException(this)
            }
        }

        existedComponent?.let {
            if (!dryRun) {
                componentRepository.save(Component(name = newName, id = it.id))
                log.info("Component with name $name updated to $newName")
            } else {
                log.info("Component with name $name will be updated to $newName")
            }
        } ?: run {
            log.warn("Component with name $name not found in DMS")
            if (component == null) {
                throw NotFoundException("None of $name and $newName components were found in DMS")
            }
            log.info("Component $name already renamed to $newName")
        }
    }

    /**
     * Check if component with name [name] exists in components registry
     * @param name - the component name
     * @return true if component with name [name] exists in components registry
     */
    private fun isComponentPresentInRegistry(name: String) = try {
        componentsRegistryService.getComponent(name)
        true
    } catch (e: ComponentsRegistryNotFoundException) {
        log.info("Component with name $name not found in components registry")
        false
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminServiceImpl::class.java)
    }
}