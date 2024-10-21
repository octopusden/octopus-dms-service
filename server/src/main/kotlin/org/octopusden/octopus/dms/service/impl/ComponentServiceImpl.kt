package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.DependencyDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.dto.ComponentVersionStatusWithInfoDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.octopusden.octopus.dms.event.DeleteComponentVersionArtifactEvent
import org.octopusden.octopus.dms.event.RegisterComponentVersionArtifactEvent
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.VersionFormatIsNotValidException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.dms.service.StorageService
import org.octopusden.octopus.releasemanagementservice.client.ReleaseManagementServiceClient
import org.octopusden.releng.versions.NumericVersionFactory
import org.slf4j.LoggerFactory
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
    private val releaseManagementServiceClient: ReleaseManagementServiceClient,
    private val componentVersionArtifactMapper: ComponentVersionArtifactMapper
) : ComponentService {

    override fun getComponents(filter: ComponentRequestFilter?): List<ComponentDTO> {
        log.info("Get components")
        return componentsRegistryService.getExternalComponents(filter).sortedWith { a, b ->
            a.name.lowercase().compareTo(b.name.lowercase())
        }
    }

    override fun getDependencies(componentName: String, version: String): List<DependencyDTO> {
        log.info("Get dependencies of '$componentName:$version'")
        return componentVersionRepository.findByComponentNameAndVersion(componentName, version)
            ?.let { componentVersion ->
                val components = componentsRegistryService.getExternalComponents(null).associateBy { c -> c.id }
                return releaseManagementServiceClient.getBuild(
                    componentVersion.component.name,
                    componentVersion.version
                ).dependencies.mapNotNull { (component, version, _) ->
                    componentVersionRepository.findByComponentNameAndVersion(component, version)
                }.map { cv ->
                    DependencyDTO(components[cv.component.name]!!, cv.version, BuildStatus.UNKNOWN_STATUS)
                }.sortedWith { a, b ->
                    a.component.name.lowercase().compareTo(b.component.name.lowercase()).takeIf { it != 0 }
                        ?: a.version.compareTo(b.version)
                }
            } ?: throw NotFoundException("Version '$version' is not found for component '$componentName'")
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
                                componentName, componentVersion.version, componentVersionArtifactMapper.mapToFullDTO(it)
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
    override fun getComponentVersions(
        componentName: String, minorVersions: List<String>, includeRc: Boolean
    ): List<ComponentVersionStatusWithInfoDTO> {
        log.info("Get versions of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val componentVersionEntities = if (minorVersions.isEmpty()) {
            componentVersionRepository.findByComponentName(componentName)
        } else {
            componentVersionRepository.findByComponentNameAndMinorVersions(componentName, minorVersions)
        }
        val allowedStatuses = if (includeRc) {
            arrayOf(BuildStatus.RELEASE, BuildStatus.RC)
        } else {
            arrayOf(BuildStatus.RELEASE)
        }
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        return if (componentVersionEntities.isEmpty()) {
            emptyList()
        } else {
            val componentBuilds = releaseManagementService.getComponentBuilds(
                componentName, allowedStatuses, componentVersionEntities.map { it.version }.toSet()
            ).associateBy { build -> build.version }
            componentVersionEntities.map { cv ->
                ComponentVersionStatusWithInfoDTO(
                    cv.component.name,
                    cv.version,
                    componentBuilds[cv.version]?.status ?: BuildStatus.UNKNOWN_STATUS,
                    numericVersionFactory.create(cv.version)
                )
            }.filter {
                allowedStatuses.contains(it.status)
            }
        }
    }

    @Transactional(readOnly = false)
    override fun deleteComponentVersion(componentName: String, version: String, dryRun: Boolean) {
        log.info("Delete version '$version' of component '$componentName'")
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(componentName, version)
            ?: with(normalizeComponentVersion(componentName, version)) {
                componentVersionRepository.findByComponentNameAndVersion(componentName, this.second)
            }
        componentVersion?.let {
            if (!dryRun) {
                componentVersionArtifactRepository.findByComponentVersion(it).forEach { componentVersionArtifact ->
                    applicationEventPublisher.publishEvent(
                        DeleteComponentVersionArtifactEvent(
                            componentName, it.version,  componentVersionArtifactMapper.mapToFullDTO(componentVersionArtifact)
                        )
                    )
                }
                componentVersionRepository.delete(it)
            }
            log.info("$it deleted")
        }
    }

    @Transactional(readOnly = true)
    override fun getPreviousLinesLatestVersions(
        componentName: String,
        version: String,
        includeRc: Boolean
    ): List<String> {
        log.info("Get previous versions for version '$version' of component '$componentName'" + if (includeRc) " including RC" else "")
        componentsRegistryService.checkComponent(componentName)
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        releaseManagementService.getComponentBuild(componentName, buildVersion, null)
        val versions = componentVersionRepository.findByComponentName(componentName).map { it.version }.toSet()
        val allowedStatuses = if (includeRc) {
            arrayOf(BuildStatus.RELEASE, BuildStatus.RC)
        } else {
            arrayOf(BuildStatus.RELEASE)
        }
        val builds = releaseManagementService.getComponentBuilds(componentName, allowedStatuses, versions)
        return componentsRegistryService.findPreviousLines(componentName, version, builds.map { it.version })
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifacts(
        componentName: String,
        version: String,
        type: ArtifactType?
    ): ArtifactsDTO {
        log.info("Get artifacts" + (type?.let { " with type '$it'" } ?: "") + " for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        val build = releaseManagementService.getComponentBuild(componentName, buildVersion, type)
        val artifacts = componentVersionArtifactMapper.mapToShortDTOList(
            if (type == null) {
                componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersion(
                    componentName,
                    buildVersion
                )
            } else {
                componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndType(
                    componentName,
                    buildVersion,
                    type
                )
            }
        )
        return ArtifactsDTO(
            build,
            artifacts //TODO: filter distribution artifacts if version status is RC?
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ): ArtifactFullDTO {
        log.info("Get artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        return componentVersionArtifactMapper.mapToFullDTO(getOrElseThrow(componentName, buildVersion, artifactId))
            .also {
                releaseManagementService.getComponentBuild(componentName, buildVersion, it.type)
            }
    }

    @Transactional(readOnly = true)
    override fun downloadComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ): DownloadArtifactDTO {
        log.info("Download artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        val componentVersionArtifactEntity = getOrElseThrow(componentName, buildVersion, artifactId)
        if (componentVersionArtifactEntity.artifact.repositoryType == RepositoryType.DOCKER) {
            throw UnsupportedOperationException("Downloading of Docker artifacts is not supported.")
        }
        releaseManagementService.getComponentBuild(componentName, buildVersion, componentVersionArtifactEntity.type)
        return DownloadArtifactDTO(
            componentVersionArtifactEntity.artifact.fileName,
            storageService.download(componentVersionArtifactEntity.artifact, false)
        )
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
        componentsRegistryService.checkComponent(componentName)
        val (minorVersion, buildVersion) = normalizeComponentVersion(componentName, version)
        releaseManagementService.getComponentBuild(componentName, buildVersion, registerArtifactDTO.type)
        val artifact = artifactRepository.findById(artifactId).orElseThrow {
            NotFoundException("Artifact with ID '$artifactId' is not found")
        }
        storageService.find(artifact, false)
        componentRepository.lock(componentName.hashCode()) // prevent race condition inserting component/version/artifact
        val component = componentRepository.findByName(componentName)
            ?: componentRepository.save(Component(name = componentName))
        val componentVersion = componentVersionRepository.findByComponentAndVersion(component, buildVersion)
            ?: componentVersionRepository.save(
                ComponentVersion(
                    component = component,
                    minorVersion = minorVersion,
                    version = buildVersion
                )
            )
        val componentVersionArtifact =
            componentVersionArtifactRepository.findByComponentVersionAndArtifact(componentVersion, artifact)?.let {
                with("Artifact with ID '$artifactId' is already registered for version '$buildVersion' of component '$componentName'") {
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
        return componentVersionArtifactMapper.mapToFullDTO(componentVersionArtifact).also {
            applicationEventPublisher.publishEvent(
                RegisterComponentVersionArtifactEvent(componentName, buildVersion, it)
            )
        }
    }

    @Transactional(readOnly = false)
    override fun deleteComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
        dryRun: Boolean
    ) {
        log.info("Delete artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
            componentName, buildVersion, artifactId
        )?.let {
            if (!dryRun) {
                applicationEventPublisher.publishEvent(
                    DeleteComponentVersionArtifactEvent(
                        componentName,
                        buildVersion,
                        componentVersionArtifactMapper.mapToFullDTO(it)
                    )
                )
                componentVersionArtifactRepository.delete(it)
            }
            log.info("$it deleted")
        }
    }

    private fun getOrElseThrow(componentName: String, version: String, artifactId: Long) =
        componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
            componentName, version, artifactId
        ) ?: throw NotFoundException("Artifact with ID '$artifactId' is not found for version '$version' of component '$componentName'")

    private fun normalizeComponentVersion(componentName: String, version: String): Pair<String, String> {
        val detailedComponentVersion = componentsRegistryService.getDetailedComponentVersion(componentName, version)
        return detailedComponentVersion.minorVersion.version to (when (version) {
            detailedComponentVersion.buildVersion.version -> version

            detailedComponentVersion.releaseVersion.version -> {
                try {
                    releaseManagementService.getComponentBuild(componentName, version)
                        .takeIf { build -> build.status in normalizeStatuses }
                        ?.let { build ->
                            val buildVersion = build.version
                            log.info("Release version '$version' of component '$componentName' is normalized to build version '$buildVersion'")
                            buildVersion
                        }
                } catch (e: org.octopusden.octopus.releasemanagementservice.client.common.exception.NotFoundException) {
                    null
                } ?: throw NotFoundException("Unable to normalize version '$version' of component '$componentName'")
            }

            else -> {
                throw VersionFormatIsNotValidException("Version '$version' of component '$componentName' does not fit RELEASE or BUILD format")
            }
        })
    }

    companion object {
        private val normalizeStatuses = arrayOf(BuildStatus.RC, BuildStatus.RELEASE)
        private val log = LoggerFactory.getLogger(ComponentServiceImpl::class.java)
    }
}