package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.exception.IllegalComponentRenamingException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.service.AdminService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.dms.service.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = false)
class AdminServiceImpl( //TODO: move functionality to ComponentService and ArtifactService?
    private val componentsRegistryService: ComponentsRegistryService,
    private val releaseManagementService: ReleaseManagementService,
    private val storageService: StorageService,
    private val componentRepository: ComponentRepository,
    private val componentVersionRepository: ComponentVersionRepository,
    private val componentVersionArtifactRepository: ComponentVersionArtifactRepository,
    private val artifactRepository: ArtifactRepository
) : AdminService {
    /**
     * Remove all implicit/internal components.
     *
     * Those components are non-accessible via DMS (filtered out) therefore no locking required.
     * No "delete" events should be generated (this requirement could be changed later).
     *
     * @param dryRun - if true, do not delete versions
     */
    override fun deleteInvalidComponents(dryRun: Boolean) {
        val validComponents = componentsRegistryService.getExternalComponents(
            ComponentRequestFilter()
        ).map { it.name }.toSet()
        val invalidComponents = componentRepository.findAll().filter { !validComponents.contains(it.name) }
        log.info("Delete invalid components $invalidComponents")
        if (!dryRun) {
            componentRepository.deleteAll(invalidComponents)
            log.info("${invalidComponents.size} invalid component(s) deleted")
        }
    }

    /**
     * Remove all component versions in BUILD status.
     *
     * Those versions are non-accessible via DMS (filtered out) therefore no locking required.
     * No "delete" events should be generated (this requirement could be changed later).
     *
     * @param dryRun - if true, do not delete versions
     */
    override fun deleteInvalidComponentsVersions(dryRun: Boolean) {
        val invalidComponentsVersions = componentVersionRepository.findAll().groupBy {
            it.component.name
        }.flatMap { (componentName, versions) ->
            val validComponentVersions = releaseManagementService.findReleases(
                componentName, versions.map { it.version }, includeRc = true
            ).map { it.version }.toSet()
            componentVersionRepository.findByComponentName(componentName).filter {
                !validComponentVersions.contains(it.version)
            }
        }
        log.info("Delete invalid components versions $invalidComponentsVersions")
        if (!dryRun) {
            componentVersionRepository.deleteAll(invalidComponentsVersions)
            log.info("${invalidComponentsVersions.size} invalid component version(s) deleted")
        }
    }

    /**
     * Remove all artifacts unobtainable from Artifactory.
     *
     * If artifact is registered for some component versions no "delete" events should be generated (this requirement could be changed later).
     *
     * This is "dummy" clean up implementation, use with extreme caution!
     *
     * @param updateSha256 - if true, update sha256 for artifacts (in other case, consider all artifacts with irrelevant sha256 as unobtainable)
     * @param dryRun - if true, do not update/delete artifacts
     */
    override fun deleteInvalidArtifacts(updateSha256: Boolean, dryRun: Boolean) = artifactRepository.findAll().forEach {
        //TODO: implement batch processing with transaction propagation REQUIRES_NEW etc.
        val sha256 = try {
            storageService.find(it.repositoryType, true, it.path)?.checksums?.sha256
        } catch (e: Exception) {
            log.warn("Unable to check obtainability of artifact with ID '${it.id}'")
            return@forEach
        }
        if (sha256 != it.sha256) {
            if (updateSha256 && sha256 != null) {
                log.info("Update SHA256 checksum from ${it.sha256} to $sha256 for artifact with ID '${it.id}'")
                if (!dryRun) {
                    it.sha256 = sha256
                    artifactRepository.save(it)
                }
            } else {
                log.info("Delete unobtainable artifact with ID '${it.id}'")
                if (!dryRun) {
                    artifactRepository.delete(it)
                }
            }
        }
    }

    /**
     * Remove all artifacts not registered for at least one component version.
     *
     * This is "dummy" housekeeping of Artifact entities, use with extreme caution!
     *
     * @param dryRun - if true, do not delete artifacts
     */
    override fun deleteOrphanedArtifacts(dryRun: Boolean) {
        //TODO:
        // - add lock preventing from addition/uploading new artifacts during housekeeping
        // - add artifact.createdAt column and use for filtering to prevent removing of newly added artifacts
        // - remove "on delete cascade" from component_version_artifact.artifact_Id column
        // - schedule execution
        val orphanedArtifacts = artifactRepository.findAll().filter {
            componentVersionArtifactRepository.findByArtifact(it).isEmpty()
        }
        log.info("Delete orphaned artifacts $orphanedArtifacts")
        if (!dryRun) {
            artifactRepository.deleteAll(orphanedArtifacts)
            log.info("${orphanedArtifacts.size} orphaned artifact(s) deleted")
        }
    }

    /**
     * Update component name and return new component name.
     *
     * By this point, the component should have already been renamed in 'releng'.
     *
     * @param name - old component name
     * @param newName - new component name
     * @param dryRun - if true, do not update component name
     */
    @Transactional(readOnly = false)
    override fun renameComponent(name: String, newName: String, dryRun: Boolean) {
        log.info("Update component name from '$name' to '$newName'")

        if (componentsRegistryService.isComponentExists(name)) {
            log.error("Component with name $name exists in components registry")
            throw IllegalComponentRenamingException("Component with name $name exists in components registry")
        }
        if (!componentsRegistryService.isComponentExists(newName)) {
            log.error("Component with name $newName not found in components registry")
            throw NotFoundException("Component with name $newName not found in components registry")
        }
        if (!releaseManagementService.isComponentExists(newName)) {
            throw NotFoundException("Component with name $newName not found in releng")
        }

        componentRepository.lock(name.hashCode())
        val existedComponent = componentRepository.findByName(name)
        componentRepository.lock(newName.hashCode())
        val component = componentRepository.findByName(newName)

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

    companion object {
        private val log = LoggerFactory.getLogger(AdminServiceImpl::class.java)
    }
}