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
import org.octopusden.octopus.dms.dto.ComponentVersionWithInfoDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.dto.ReleaseDTO
import org.octopusden.octopus.dms.dto.ReleaseFullDTO
import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.octopusden.octopus.dms.event.DeleteComponentVersionArtifactEvent
import org.octopusden.octopus.dms.event.RegisterComponentVersionArtifactEvent
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
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
class ComponentServiceImpl(
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

    override fun getComponents(filter: ComponentRequestFilter?): List<ComponentDTO> {
        log.info("Get components")
        return componentsRegistryService.getExternalComponents(filter).sortedWith { a, b ->
            a.name.lowercase().compareTo(b.name.lowercase())
        }
    }

    @Transactional(readOnly = false)
    override fun deleteComponent(componentName: String, dryRun: Boolean) {
        log.info("Delete component '$componentName'")
        componentRepository.findByName(componentName)?.let { component ->
            if (!dryRun) {
                componentVersionRepository.findByComponent(component).forEach { componentVersion ->
                    componentVersionArtifactRepository.findByComponentVersion(componentVersion).forEach {
                        applicationEventPublisher.publishEvent(
                            DeleteComponentVersionArtifactEvent(
                                componentName, componentVersion.version, it.toFullDTO(dockerRegistry)
                            )
                        )
                    }
                }
                componentRepository.delete(component)
            }
            log.info("$component deleted")
        }
    }

    @Transactional(readOnly = true)
    override fun getComponentMinorVersions(componentName: String): Set<String> {
        log.info("Get minor versions of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        return componentVersionRepository.getMinorVersionsByComponentName(componentName)
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionsWithInfo(
        componentName: String, minorVersions: List<String>, includeRc: Boolean
    ): List<ComponentVersionWithInfoDTO> {
        log.info("Get versions of component '$componentName'")
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        return getComponentVersions(componentName, minorVersions, includeRc).map {
            ComponentVersionWithInfoDTO(it, numericVersionFactory.create(it.version))
        }
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionDependencies(
        componentName: String, version: String
    ): List<ComponentVersionWithInfoDTO> {
        log.info("Get dependencies of version '$version' of component '$componentName'")
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        val release = releaseManagementService.getRelease(componentName, version, true)
        return release.dependencies.mapNotNull { dependency ->
            componentVersionRepository.findByComponentNameAndVersion(
                dependency.component, dependency.buildVersion
            )?.let {
                releaseManagementService.findRelease(it.component.name, it.version, true)?.let { dependencyRelease ->
                    ComponentVersionWithInfoDTO(it.toDTO(dependencyRelease), numericVersionFactory.create(it.version))
                }
            }
        }
    }

    @Transactional(readOnly = false)
    override fun deleteComponentVersion(componentName: String, version: String, dryRun: Boolean) {
        log.info("Delete version '$version' of component '$componentName'")
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(componentName, version)
            ?: releaseManagementService.findRelease(componentName, version, true)?.let {
                componentVersionRepository.findByComponentNameAndVersion(componentName, it.buildVersion)
            }
        componentVersion?.let {
            if (!dryRun) {
                componentVersionArtifactRepository.findByComponentVersion(it).forEach { componentVersionArtifact ->
                    applicationEventPublisher.publishEvent(
                        DeleteComponentVersionArtifactEvent(
                            componentName, it.version, componentVersionArtifact.toFullDTO(dockerRegistry)
                        )
                    )
                }
                componentVersionRepository.delete(it)
            }
            log.info("$it deleted")
        }
    }

    @Transactional(readOnly = false)
    override fun patchComponentVersion(
        componentName: String, version: String, patchComponentVersionDTO: PatchComponentVersionDTO
    ): ComponentVersionFullDTO {
        val action = if (patchComponentVersionDTO.published) "Publish" else "Revoke"
        log.info("$action version '$version' of component '$componentName'")
        val release = releaseManagementService.getRelease(componentName, version, !patchComponentVersionDTO.published)
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(
            componentName, release.buildVersion
        ) ?: throw NotFoundException("Version '${release.buildVersion}' is not found for component '$componentName'")
        return componentVersionRepository.save(
            componentVersion.apply { published = patchComponentVersionDTO.published }
        ).toFullDTO(release)
    }

    @Transactional(readOnly = true)
    override fun getPreviousLinesLatestVersions(
        componentName: String, version: String, includeRc: Boolean
    ): List<String> {
        log.info("Get previous versions for version '$version' of component '$componentName'" + if (includeRc) " including RC" else "")
        return componentsRegistryService.findPreviousLines(
            componentName,
            releaseManagementService.getRelease(componentName, version, true).buildVersion,
            getComponentVersions(componentName, emptyList(), includeRc).map { it.version }
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifacts(
        componentName: String, version: String, type: ArtifactType?
    ): ArtifactsDTO {
        log.info("Get artifacts" + (type?.let { " with type '$it'" } ?: "") + " for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val release = releaseManagementService.getRelease(componentName, version, true)
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(
            componentName, release.buildVersion
        )
        val componentVersionArtifacts = if (componentVersion != null)
            if (type != null) componentVersionArtifactRepository.findByComponentVersionAndType(componentVersion, type)
            else componentVersionArtifactRepository.findByComponentVersion(componentVersion)
        else emptyList()
        return ArtifactsDTO(
            componentVersion?.toFullDTO(release) ?: release.toComponentVersionFullDTO(),
            componentVersionArtifacts.map { it.toShortDTO(dockerRegistry) }
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ): ArtifactFullDTO {
        log.info("Get artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val buildVersion = releaseManagementService.getRelease(componentName, version, true).buildVersion
        return getComponentVersionArtifactEntity(componentName, buildVersion, artifactId).toFullDTO(dockerRegistry)
    }

    @Transactional(readOnly = true)
    override fun downloadComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ): DownloadArtifactDTO {
        log.info("Download artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val buildVersion = releaseManagementService.getRelease(componentName, version, true).buildVersion
        return getComponentVersionArtifactEntity(componentName, buildVersion, artifactId).let {
            DownloadArtifactDTO(
                it.artifact.fileName,
                storageService.download(it.artifact, false)
            )
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
        componentsRegistryService.checkComponent(componentName)
        val release = releaseManagementService.getRelease(
            componentName,
            version,
            registerArtifactDTO.type != ArtifactType.DISTRIBUTION
        )
        componentRepository.lock(componentName.hashCode()) // prevent race condition inserting component/version/artifact
        val component = componentRepository.findByName(componentName)
            ?: componentRepository.save(Component(name = componentName))
        val componentVersion = componentVersionRepository.findByComponentAndVersion(component, release.buildVersion)
            ?: componentVersionRepository.save(
                ComponentVersion(
                    component = component,
                    minorVersion = componentsRegistryService.getDetailedComponentVersion(
                        componentName,
                        release.buildVersion
                    ).minorVersion.version,
                    version = release.buildVersion
                )
            )
        val componentVersionArtifact =
            componentVersionArtifactRepository.findByComponentVersionAndArtifact(componentVersion, artifact)?.let {
                with("Artifact with ID '$artifactId' is already registered for version '${release.buildVersion}' of component '$componentName'") {
                    if (failOnAlreadyExists) {
                        throw ArtifactAlreadyExistsException(this)
                    }
                    log.info(this)
                }
                it
            } ?: componentVersionArtifactRepository.save(
                ComponentVersionArtifact(
                    componentVersion = componentVersion,
                    artifact = artifact,
                    type = registerArtifactDTO.type
                )
            )
        return componentVersionArtifact.toFullDTO(dockerRegistry).also {
            applicationEventPublisher.publishEvent(
                RegisterComponentVersionArtifactEvent(componentName, release.buildVersion, it)
            )
        }
    }

    @Transactional(readOnly = false)
    override fun deleteComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long, dryRun: Boolean
    ) {
        log.info("Delete artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        val buildVersion = releaseManagementService.findRelease(componentName, version, true)?.buildVersion ?: version
        componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
            componentName, buildVersion, artifactId
        )?.let {
            if (!dryRun) {
                applicationEventPublisher.publishEvent(
                    DeleteComponentVersionArtifactEvent(componentName, buildVersion, it.toFullDTO(dockerRegistry))
                )
                componentVersionArtifactRepository.delete(it)
            }
            log.info("$it deleted")
        }
    }

    private fun getComponentVersions(
        componentName: String, minorVersions: List<String>, includeRc: Boolean
    ): List<ComponentVersionDTO> {
        componentsRegistryService.checkComponent(componentName)
        val componentVersions = if (minorVersions.isEmpty()) {
            componentVersionRepository.findByComponentName(componentName)
        } else {
            componentVersionRepository.findByComponentNameAndMinorVersions(componentName, minorVersions)
        }
        return if (componentVersions.isEmpty()) {
            emptyList()
        } else {
            val releases = releaseManagementService.findReleases(
                componentName, componentVersions.map { it.version }, includeRc
            ).associateBy { it.buildVersion }
            componentVersions.mapNotNull {
                releases[it.version]?.let { release -> it.toDTO(release) }
            }
        }
    }

    private fun getComponentVersionArtifactEntity(
        componentName: String, version: String, artifactId: Long
    ) = componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
        componentName, version, artifactId
    )
        ?: throw NotFoundException("Artifact with ID '$artifactId' is not found for version '$version' of component '$componentName'")

    private fun ReleaseFullDTO.toComponentVersionFullDTO() = ComponentVersionFullDTO(
        componentName, buildVersion, false, status, promotedAt
    )

    private fun ComponentVersion.toDTO(release: ReleaseDTO) = ComponentVersionDTO(
        component.name, version, published, release.status
    )

    private fun ComponentVersion.toFullDTO(release: ReleaseFullDTO) = ComponentVersionFullDTO(
        component.name, version, published, release.status, release.promotedAt
    )

    companion object {
        private val log = LoggerFactory.getLogger(ComponentServiceImpl::class.java)
    }
}