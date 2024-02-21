package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.SecurityGroupsDTO
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
import org.octopusden.octopus.dms.service.RelengService
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
    private val relengService: RelengService
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

    /**
     * Update component name and return new component name.
     * By this point, the component should have already been renamed in 'releng'.
     * @param name - old component name
     * @param newName - new component name
     * @param dryRun - if true, do not update component name
     * @return new component name
     * @throws NotFoundException if component with name [name] not found in releng
     */
    @Transactional(readOnly = false)
    override fun renameComponent(name: String, newName: String, dryRun: Boolean): ComponentDTO {
        log.debug("Update component name from '$name' to '$newName'")

        if (checkComponentExistsInRS(newName)) {
            log.error("Component with name $newName exists in components registry")
            throw IllegalComponentRenamingException("Component with name $newName exists in components registry")
        }
        if (!checkComponentExistsInRS(name)) {
            log.error("Component with name $name not found in components registry")
            throw IllegalComponentRenamingException("Component with name $name not found in components registry")
        }
        checkComponentExistsInReleng(newName)
        val component = componentRepository.findByName(newName)
        val existedComponent = componentRepository.findByName(name)

        if (existedComponent != null && component != null) {
            log.error("Component with name $name and component with name $newName exists in DMS")
            throw IllegalComponentRenamingException("Component with name $newName already exists in DMS")
        }

        return existedComponent?.let {
            return if (!dryRun) {
                val component = componentRepository.save(Component(name = newName, id = it.id))
                log.info("Component with name $name updated to $newName")
                return createComponentDTO(component.id.toString(), component.name)
            } else {
                log.info("Component with name $name will be updated to $newName")
                return createComponentDTO(it.id.toString(), newName)
            }
        } ?: run {
            log.warn("Component with name $name not found in DMS")
            if (component == null) {
                throw NotFoundException("Component with name $name not found in DMS")
            }
            log.info("Component with name $newName found in DMS")
            return createComponentDTO(component.id.toString(), component.name)
        }
    }

    /**
     * Check if component with name [newName] exists in components registry
     * @param name - the component name
     * @return true if component with name [newName] exists in components registry
     */
    private fun checkComponentExistsInRS(name: String): Boolean {
        return try{
            componentsRegistryService.checkComponent(name)
            return true
        } catch (e: ComponentsRegistryNotFoundException) {
            log.error("Component with name $name not found in components registry", e)
            return false
        }
    }

    private fun createComponentDTO(id: String, name: String): ComponentDTO {
        return ComponentDTO(
            id = id,
            name = name,
            clientCode = null,
            parentComponent = null,
            securityGroups = SecurityGroupsDTO(emptyList())
        )
    }

    /**
     * Check if component with name [componentName] exists in releng
     * @param componentName - new component name
     * @throws NotFoundException if component with name [componentName] not found in releng
     */
    private fun checkComponentExistsInReleng(componentName: String) {
        relengService.getComponentBuilds(
            componentName,
            emptyArray(),
            emptyArray(),
            VersionField.VERSION
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminServiceImpl::class.java)
    }
}