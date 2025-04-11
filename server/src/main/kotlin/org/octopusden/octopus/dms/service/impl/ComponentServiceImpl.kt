package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionFullDTO
import org.octopusden.octopus.dms.client.common.dto.PatchComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.dto.BuildDTO
import org.octopusden.octopus.dms.dto.ComponentVersionWithInfoDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.dto.ReleaseDTO
import org.octopusden.octopus.dms.dto.ReleaseFullDTO
import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.octopusden.octopus.dms.event.DeleteComponentVersionArtifactEvent
import org.octopusden.octopus.dms.event.PublishComponentVersionEvent
import org.octopusden.octopus.dms.event.RegisterComponentVersionArtifactEvent
import org.octopusden.octopus.dms.event.RevokeComponentVersionEvent
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.IllegalComponentTypeException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.VersionPublishedException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.repository.getByComponentNameAndVersion
import org.octopusden.octopus.dms.repository.getByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.dms.service.StorageService
import org.octopusden.releng.versions.NumericVersionFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ComponentServiceImpl( //TODO: move "start operation" logging to ComponentController
    private val componentsRegistryService: ComponentsRegistryService,
    private val releaseManagementService: ReleaseManagementService,
    private val storageService: StorageService,
    private val componentRepository: ComponentRepository,
    private val componentVersionRepository: ComponentVersionRepository,
    private val componentVersionArtifactRepository: ComponentVersionArtifactRepository,
    private val artifactRepository: ArtifactRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @Value("\${dms-service.docker-registry}") private val dockerRegistry: String
) : ComponentService {
    /* Implementation notes:
     *
     * Use non-limited waiting pessimistic transaction advisory locks by component (see `componentRepository.lock`) because of:
     * - "on-demand" nature of Component and ComponentVersion entities
     * - "administrative only" and "safe" delete operation
     * - low concurrency (the main case is race condition on saving Component and ComponentVersion entities during parallel registration of artifacts)
     */

    override fun getComponents(filter: ComponentRequestFilter?): List<ComponentDTO> {
        log.info("Get components")
        return componentsRegistryService.getExternalComponents(filter).sortedWith { a, b ->
            a.name.lowercase().compareTo(b.name.lowercase())
        }
    }

    @Transactional(readOnly = true)
    override fun getComponentMinorVersions(componentName: String): Set<String> {
        log.info("Get minor versions of component '$componentName'")
        getExternalExplicitComponent(componentName)
        return componentVersionRepository.getMinorVersionsByComponentName(componentName)
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionsWithInfo(
        componentName: String, minorVersions: List<String>, includeRc: Boolean
    ): List<ComponentVersionWithInfoDTO> {
        log.info("Get versions of component '$componentName'")
        val componentVersions = getComponentVersions(componentName, minorVersions, includeRc)
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        return componentVersions.map { ComponentVersionWithInfoDTO(it, numericVersionFactory.create(it.version)) }
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionDependencies(
        componentName: String, version: String
    ): List<ComponentVersionWithInfoDTO> {
        log.info("Get dependencies of version '$version' of component '$componentName'")
        if (!getExternalExplicitComponent(componentName).solution) {
            throw IllegalComponentTypeException("Component '$componentName' is not solution")
        }
        val release = releaseManagementService.getRelease(componentName, version, true)
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        return release.dependencies.mapNotNull { dependency ->
            componentVersionRepository.findByComponentNameAndVersion(
                dependency.component, dependency.version
            )?.let {
                releaseManagementService.findRelease(it.component.name, it.version, true)?.let { dependencyRelease ->
                    ComponentVersionWithInfoDTO(it.toDTO(dependencyRelease), numericVersionFactory.create(it.version))
                }
            }
        }
    }

    @Transactional(readOnly = false)
    override fun patchComponentVersion(
        componentName: String, version: String, patchComponentVersionDTO: PatchComponentVersionDTO
    ): ComponentVersionDTO {
        log.info("${if (patchComponentVersionDTO.published) "Publish" else "Revoke"} version '$version' of component '$componentName'")
        val component = getExternalExplicitComponent(componentName)
        val release = releaseManagementService.getRelease(component.id, version, !patchComponentVersionDTO.published)
        componentRepository.lock(component.id.hashCode())
        val componentVersion = componentVersionRepository.getByComponentNameAndVersion(component.id, release.version)
        return if (componentVersion.published == patchComponentVersionDTO.published) {
            componentVersion.toDTO(release)
        } else {
            if (patchComponentVersionDTO.published && component.solution) {
                release.dependencies.filter {
                    componentsRegistryService.getExternalComponent(it.component).explicit && !it.isPublished()
                }.takeIf { it.isNotEmpty() }?.let {
                    throw VersionPublishedException("Unable to publish version '${release.version}' of solution '${component.id}'. It has unpublished explicit dependencies $it")
                }
            } else if (!patchComponentVersionDTO.published && !component.solution) {
                release.parents.filter {
                    componentsRegistryService.getExternalComponent(it.component).solution && it.isPublished()
                }.takeIf { it.isNotEmpty() }?.let {
                    throw VersionPublishedException("Unable to revoke version '${release.version}' of component '${component.id}'. It is dependency of published solutions $it")
                }
            }
            val artifacts = componentVersionArtifactRepository.findByComponentVersion(componentVersion).map {
                it.toShortDTO(dockerRegistry)
            }
            applicationEventPublisher.publishEvent(
                if (patchComponentVersionDTO.published) {
                    PublishComponentVersionEvent(component.id, release.version, artifacts)
                } else {
                    RevokeComponentVersionEvent(component.id, release.version, artifacts)
                }
            )
            componentVersionRepository.save(componentVersion.apply {
                published = patchComponentVersionDTO.published
            }).toDTO(release)
        }
    }

    @Transactional(readOnly = true)
    override fun getPreviousLinesLatestVersions(
        componentName: String, version: String, includeRc: Boolean
    ): List<String> {
        log.info("Get previous versions for version '$version' of component '$componentName'" + if (includeRc) " including RC" else "")
        val componentVersions = getComponentVersions(componentName, emptyList(), includeRc)
        return componentsRegistryService.findPreviousLines(
            componentName,
            releaseManagementService.getRelease(componentName, version, true).version,
            componentVersions.map { it.version }
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifacts(
        componentName: String, version: String, type: ArtifactType?
    ): ArtifactsDTO {
        log.info("Get artifacts" + (type?.let { " with type '$it'" } ?: "") + " for version '$version' of component '$componentName'")
        val component = getExternalExplicitComponent(componentName)
        val release = releaseManagementService.getRelease(component.id, version, true)
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(component.id, release.version)
        val componentVersionArtifacts = if (componentVersion != null)
            if (type != null) componentVersionArtifactRepository.findByComponentVersionAndType(componentVersion, type)
            else componentVersionArtifactRepository.findByComponentVersion(componentVersion)
        else emptyList()
        return ArtifactsDTO(
            componentVersion?.toFullDTO(component, release) ?: release.toComponentVersionFullDTO(component),
            componentVersionArtifacts.map { it.toShortDTO(dockerRegistry) }
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ): ArtifactFullDTO {
        log.info("Get artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        return getComponentVersionArtifactEntity(componentName, version, artifactId).toFullDTO(dockerRegistry)
    }

    @Transactional(readOnly = true)
    override fun downloadComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ): DownloadArtifactDTO {
        log.info("Download artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        return getComponentVersionArtifactEntity(componentName, version, artifactId).let {
            DownloadArtifactDTO(it.artifact.fileName, storageService.download(it.artifact, false))
        }
    }

    @Transactional(readOnly = false)
    override fun registerComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
        failOnAlreadyExists: Boolean,
        registerArtifactDTO: RegisterArtifactDTO
    ): ArtifactFullDTO {
        log.info("Register '${registerArtifactDTO.type}' artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        val artifact = artifactRepository.findById(artifactId).orElseThrow {
            NotFoundException("Artifact with ID '$artifactId' is not found")
        }
        storageService.find(artifact, false)
        getExternalExplicitComponent(componentName)
        val release = releaseManagementService.getRelease(
            componentName,
            version,
            registerArtifactDTO.type != ArtifactType.DISTRIBUTION
        )
        componentRepository.lock(componentName.hashCode())
        val component = componentRepository.findByName(componentName)
            ?: componentRepository.save(Component(name = componentName))
        val componentVersion = componentVersionRepository.findByComponentAndVersion(component, release.version)
            ?: componentVersionRepository.save(
                ComponentVersion(
                    component = component,
                    minorVersion = componentsRegistryService.getDetailedComponentVersion(
                        componentName,
                        release.version
                    ).minorVersion.version,
                    version = release.version
                )
            )
        val componentVersionArtifact = componentVersionArtifactRepository.findByComponentVersionAndArtifact(
            componentVersion, artifact
        )
        return if (componentVersionArtifact != null) {
            val message = "Artifact with ID '$artifactId' is already registered for version '${release.version}' of component '$componentName'"
            if (failOnAlreadyExists) throw ArtifactAlreadyExistsException(message)
            else log.info(message)
            componentVersionArtifact.toFullDTO(dockerRegistry)
        } else {
            if (componentVersion.published) {
                throw VersionPublishedException("Version '${release.version}' of component '$componentName' is published. Unable to register '${registerArtifactDTO.type}' artifact with ID '$artifactId' for the component version")
            }
            componentVersionArtifactRepository.save(
                ComponentVersionArtifact(
                    componentVersion = componentVersion,
                    artifact = artifact,
                    type = registerArtifactDTO.type
                )
            ).toFullDTO(dockerRegistry).also {
                applicationEventPublisher.publishEvent(
                    RegisterComponentVersionArtifactEvent(componentName, release.version, it)
                )
            }
        }
    }

    @Transactional(readOnly = false)
    override fun deleteComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long, dryRun: Boolean
    ) {
        log.info("Delete artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        val buildVersion = releaseManagementService.findRelease(componentName, version, true)?.version ?: version
        componentRepository.lock(componentName.hashCode())
        componentVersionRepository.findByComponentNameAndVersion(componentName, buildVersion)?.let { componentVersion ->
            if (componentVersion.published) {
                throw VersionPublishedException("Version '$buildVersion' of component '$componentName' is published. Unable to delete artifact with ID '$artifactId' for the component version")
            }
            componentVersionArtifactRepository.findByComponentVersionAndArtifactId(componentVersion, artifactId)?.let {
                if (!dryRun) {
                    applicationEventPublisher.publishEvent(
                        DeleteComponentVersionArtifactEvent(componentName, buildVersion, it.toFullDTO(dockerRegistry))
                    )
                    componentVersionArtifactRepository.delete(it)
                    if (componentVersionArtifactRepository.findByComponentVersion(componentVersion).isEmpty()) {
                        componentVersionRepository.delete(componentVersion)
                    }
                    //NOTE: Component is displayed in UI even if it has no ComponentVersions - no need to check and clean it
                    log.info("$it deleted")
                }
            }
        }
    }

    private fun getExternalExplicitComponent(componentName: String) =
        componentsRegistryService.getExternalComponent(componentName).also {
            if (!it.explicit) {
                throw IllegalComponentTypeException("Component '$componentName' is not explicit")
            }
        }

    private fun getComponentVersions(
        componentName: String, minorVersions: List<String>, includeRc: Boolean
    ): List<ComponentVersionDTO> {
        getExternalExplicitComponent(componentName)
        val componentVersions = if (minorVersions.isEmpty()) {
            componentVersionRepository.findByComponentName(componentName)
        } else {
            componentVersionRepository.findByComponentNameAndMinorVersionIn(componentName, minorVersions)
        }
        return if (componentVersions.isEmpty()) {
            emptyList()
        } else {
            val releases = releaseManagementService.findReleases(
                componentName, componentVersions.map { it.version }, includeRc
            ).associateBy { it.version }
            componentVersions.mapNotNull {
                releases[it.version]?.let { release -> it.toDTO(release) }
            }
        }
    }

    private fun getComponentVersionArtifactEntity(
        componentName: String, version: String, artifactId: Long
    ): ComponentVersionArtifact {
        getExternalExplicitComponent(componentName)
        val buildVersion = releaseManagementService.getRelease(componentName, version, true).version
        return componentVersionArtifactRepository.getByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
            componentName, buildVersion, artifactId
        )
    }

    private fun BuildDTO.isPublished() =
        componentVersionRepository.findByComponentNameAndVersion(component, version)?.published == true

    private fun ComponentVersion.toDTO(release: ReleaseDTO) = ComponentVersionDTO(
        component.name, version, published, release.status
    )

    private fun ComponentVersion.toFullDTO(component: ComponentDTO, release: ReleaseFullDTO) = ComponentVersionFullDTO(
        component.id, version, published, release.status, release.promotedAt, component.name, component.solution, component.clientCode, component.parentComponent
    )

    private fun ReleaseFullDTO.toComponentVersionFullDTO(component: ComponentDTO) = ComponentVersionFullDTO(
        component.id, version, false, status, promotedAt, component.name, component.solution, component.clientCode, component.parentComponent
    )

    companion object {
        private val log = LoggerFactory.getLogger(ComponentServiceImpl::class.java)
    }
}