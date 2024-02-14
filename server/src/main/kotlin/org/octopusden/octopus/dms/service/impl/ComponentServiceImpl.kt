package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.VersionFormatIsNotValidException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.RelengService
import org.octopusden.octopus.dms.service.StorageService
import org.octopusden.octopus.dms.service.impl.dto.ComponentVersionStatusWithInfoDTO
import org.octopusden.octopus.dms.service.impl.dto.DownloadArtifactDTO
import org.octopusden.releng.versions.NumericVersionFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ComponentServiceImpl(
    private val componentsRegistryService: ComponentsRegistryService,
    private val relengService: RelengService,
    private val storageService: StorageService,
    private val componentRepository: ComponentRepository,
    private val componentVersionRepository: ComponentVersionRepository,
    private val componentVersionArtifactRepository: ComponentVersionArtifactRepository,
    private val artifactRepository: ArtifactRepository
) : ComponentService {
    override fun getComponents(): List<ComponentDTO> {
        log.info("Get components")
        return componentsRegistryService.getComponents()
    }

    @Transactional(readOnly = false)
    override fun deleteComponent(componentName: String, dryRun: Boolean) {
        log.info("Delete component '$componentName'")
        componentRepository.findByName(componentName)?.let {
            if (!dryRun) componentRepository.delete(it)
            log.info("$it deleted")
        }
    }

    /**
     * Update component name and return new component name
     * @param componentName - old component name
     * @param newComponentName - new component name
     * @return new component name
     * @throws NotFoundException if component with name [componentName] not found in releng
     */
    @Transactional(readOnly = false)
    override fun renameComponent(componentName: String, newComponentName: String): String {
        var result = componentName
        log.debug("Update component name from '$componentName' to '$newComponentName'")

        checkComponentExists(newComponentName)
        componentRepository.findByName(componentName)?.let {
            val newComponent = componentRepository.save(Component(name = newComponentName, id = it.id))
            log.info("${it.name} updated to ${newComponent.name}")
            result = newComponent.name
        } ?: run {
            log.warn("Component with name $componentName not found in DMS")
            // Let's assume that component with name [componentName] has been renamed to [newComponentName] yet
            result = newComponentName
        }
        return result
    }

    /**
     * Check if component with name [componentName] exists in releng
     * @param componentName - new component name
     * @throws NotFoundException if component with name [componentName] not found in releng
     */
    private fun checkComponentExists(componentName: String) {
        relengService.getComponentBuilds(
            componentName,
            arrayOf(),
            arrayOf(),
            VersionField.VERSION
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentMinorVersions(componentName: String): Set<String> {
        log.info("Get minor versions of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        return componentVersionRepository.getMinorVersionsByComponentName(componentName)
    }

    @Transactional(readOnly = true)
    override fun getComponentVersions(
        componentName: String,
        minorVersions: List<String>,
        includeRc: Boolean
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
            val componentBuilds = relengService.getComponentBuilds(
                componentName,
                allowedStatuses,
                componentVersionEntities.map { it.version }.toTypedArray(),
                VersionField.VERSION
            )
            componentVersionEntities.map { cv ->
                ComponentVersionStatusWithInfoDTO(
                    cv.version,
                    componentBuilds.find { it.version == cv.version }?.status ?: BuildStatus.UNKNOWN_STATUS,
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
        componentVersionRepository.findByComponentNameAndVersion(componentName, version)?.let {
            if (!dryRun) componentVersionRepository.delete(it)
            log.info("$it deleted")
        } ?: with(normalizeComponentVersion(componentName, version)) {
            componentVersionRepository.findByComponentNameAndVersion(componentName, this.second)?.let {
                if (!dryRun) componentVersionRepository.delete(it)
                log.info("$it deleted")
            }
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
        relengService.checkVersionStatus(componentName, buildVersion, null)
        val versions = componentVersionRepository.findByComponentName(componentName).map { it.version }.toTypedArray()
        val allowedStatuses = if (includeRc) {
            arrayOf(BuildStatus.RELEASE, BuildStatus.RC)
        } else {
            arrayOf(BuildStatus.RELEASE)
        }
        val builds = relengService.getComponentBuilds(componentName, allowedStatuses, versions, VersionField.VERSION)
        return componentsRegistryService.findPreviousLines(
            componentName,
            buildVersion,
            builds.map { it.version })
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifacts(
        componentName: String,
        version: String,
        type: ArtifactType?
    ): ArtifactsDTO {
        log.info("Get artifacts" + (type?.let { " with type '$it'" }
            ?: "") + " for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        relengService.checkVersionStatus(componentName, buildVersion, type)
        return ArtifactsDTO(
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
            }.map { it.toShortDTO() } //TODO: filter distribution artifacts if version status is RC?
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long
    ): ArtifactFullDTO {
        log.info("Get artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        return getOrElseThrow(componentName, buildVersion, artifactId).toFullDTO().also {
            relengService.checkVersionStatus(componentName, buildVersion, it.type)
        }
    }

    @Transactional(readOnly = true)
    override fun downloadComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long
    ): DownloadArtifactDTO {
        log.info("Download artifact with ID '$artifactId' for version '$version' of component '$componentName'")
        componentsRegistryService.checkComponent(componentName)
        val (_, buildVersion) = normalizeComponentVersion(componentName, version)
        val componentVersionArtifactEntity = getOrElseThrow(componentName, buildVersion, artifactId)
        relengService.checkVersionStatus(componentName, buildVersion, componentVersionArtifactEntity.type)
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
        relengService.checkVersionStatus(componentName, buildVersion, registerArtifactDTO.type)
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
        return componentVersionArtifact.toFullDTO()
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
            if (!dryRun) componentVersionArtifactRepository.delete(it)
            log.info("$it deleted")
        }
    }

    private fun getOrElseThrow(componentName: String, version: String, artifactId: Long) =
        componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
            componentName, version, artifactId
        ) ?: throw NotFoundException("Artifact with ID '$artifactId' is not found for version '$version' of component '$componentName'")

    private fun normalizeComponentVersion(componentName: String, version: String): Pair<String, String> {
        val detailedComponentVersion = componentsRegistryService.getDetailedComponentVersion(componentName, version)
        return detailedComponentVersion.minorVersion.version to (
                when (version) {
                    detailedComponentVersion.buildVersion.version -> version

                    detailedComponentVersion.releaseVersion.version -> {
                        relengService.getComponentBuilds(
                            componentName,
                            arrayOf(BuildStatus.RC, BuildStatus.RELEASE),
                            arrayOf(detailedComponentVersion.releaseVersion.jiraVersion),
                            VersionField.RELEASE_VERSION
                        ).firstOrNull()?.let {
                            log.info("Release version '$version' of component '$componentName' is normalized to build version '${it.version}'")
                            it.version
                        } ?: throw NotFoundException("Unable to normalize version '$version' of component '$componentName'")
                    }

                    else -> {
                        throw VersionFormatIsNotValidException("Version '$version' of component '$componentName' does not fit RELEASE or BUILD format")
                    }
                }
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ComponentServiceImpl::class.java)
    }
}