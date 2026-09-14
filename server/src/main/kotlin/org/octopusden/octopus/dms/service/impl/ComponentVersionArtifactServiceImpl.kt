package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionFullDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.dto.BuildFullDTO
import org.octopusden.octopus.dms.dto.ComponentVersionArtifactRegistrationResult
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.entity.Artifact
import org.octopusden.octopus.dms.entity.Component
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.octopusden.octopus.dms.event.DeleteComponentVersionArtifactEvent
import org.octopusden.octopus.dms.event.RegisterComponentVersionArtifactEvent
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.ArtifactChecksumChangedException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.VersionPublishedException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.repository.getByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId
import org.octopusden.octopus.dms.service.ArtifactService
import org.octopusden.octopus.dms.service.ComponentVersionArtifactService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.dms.service.StorageService
import org.octopusden.octopus.dms.service.impl.ComponentServiceImpl.Companion.toFullDTO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest

@Service
class ComponentVersionArtifactServiceImpl(
    private val componentsRegistryService: ComponentsRegistryService,
    private val releaseManagementService: ReleaseManagementService,
    private val storageService: StorageService,
    private val componentRepository: ComponentRepository,
    private val componentVersionRepository: ComponentVersionRepository,
    private val componentVersionArtifactRepository: ComponentVersionArtifactRepository,
    private val artifactRepository: ArtifactRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val artifactService: ArtifactService,
    @param:Value("\${dms-service.docker-registry}") private val dockerRegistry: String,
) : ComponentVersionArtifactService {
    /* Implementation notes:
     *
     * Use non-limited waiting pessimistic transaction advisory locks by component (see `componentRepository.lock`) because of:
     * - "on-demand" nature of Component and ComponentVersion entities
     * - "administrative only" and "safe" delete operation
     * - low concurrency (the main case is race condition on saving Component and ComponentVersion entities during parallel registration of artifacts)
     */

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifacts(
        componentName: String,
        version: String,
        type: ArtifactType?,
    ): ArtifactsDTO {
        val component = componentsRegistryService.getExternalExplicitComponentVersion(componentName, version)
        val release = releaseManagementService.getRelease(component.id, version, true)
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(component.id, release.version)
        val componentVersionArtifacts = if (componentVersion != null) {
            if (type != null) {
                componentVersionArtifactRepository.findByComponentVersionAndType(componentVersion, type)
            } else {
                componentVersionArtifactRepository.findByComponentVersion(componentVersion)
            }
        } else {
            emptyList()
        }
        return ArtifactsDTO(
            componentVersion?.toFullDTO(component, release) ?: release.toComponentVersionFullDTO(component),
            componentVersionArtifacts.map { it.toShortDTO(dockerRegistry) },
        )
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifactFullDTOs(componentVersion: ComponentVersion): List<ArtifactFullDTO> =
        componentVersionArtifactRepository.findByComponentVersion(componentVersion).map { it.toFullDTO(dockerRegistry) }

    @Transactional(readOnly = true)
    override fun getComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
    ): ArtifactFullDTO = getComponentVersionArtifactEntity(componentName, version, artifactId).toFullDTO(dockerRegistry)

    @Transactional(readOnly = true)
    override fun downloadComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
    ): DownloadArtifactDTO =
        getComponentVersionArtifactEntity(componentName, version, artifactId).artifact.let {
            DownloadArtifactDTO(it.fileName, storageService.download(it.repositoryType, false, it.path))
        }

    @Transactional(readOnly = false)
    override fun registerComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
        failOnAlreadyExists: Boolean,
        registerArtifactDTO: RegisterArtifactDTO,
    ): ArtifactFullDTO {
        val artifact = artifactRepository.findById(artifactId).orElseThrow {
            NotFoundException("Artifact with ID '$artifactId' is not found")
        }
        throwIfArtifactChecksumChanged(artifact)
        val release = resolveRelease(componentName, version, registerArtifactDTO.type)
        componentRepository.lock(componentName.hashCode())
        val componentVersion = getOrCreateComponentVersionEntity(componentName, release.version)
        val componentVersionArtifact = componentVersionArtifactRepository.findByComponentVersionAndArtifact(
            componentVersion,
            artifact,
        )

        if (componentVersionArtifact != null) {
            return handleAlreadyRegistered(
                registration = componentVersionArtifact,
                componentName = componentName,
                version = release.version,
                failOnAlreadyExists = failOnAlreadyExists,
            )
        }

        if (componentVersion.published && registerArtifactDTO.type !in RE_REGISTRABLE_TYPES) {
            throwVersionPublished(componentVersion, componentName, registerArtifactDTO.type)
        }

        return componentVersionArtifactRepository
            .save(
                ComponentVersionArtifact(
                    componentVersion = componentVersion,
                    artifact = artifact,
                    type = registerArtifactDTO.type,
                ),
            ).toFullDTO(dockerRegistry)
            .also {
                applicationEventPublisher.publishEvent(
                    RegisterComponentVersionArtifactEvent(componentName, release.version, it),
                )
            }
    }

    @Transactional(readOnly = false)
    override fun deleteComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
        dryRun: Boolean,
    ) {
        val buildVersion = releaseManagementService.findRelease(componentName, version, true)?.version ?: version
        componentRepository.lock(componentName.hashCode())
        componentVersionRepository.findByComponentNameAndVersion(componentName, buildVersion)?.let { componentVersion ->
            if (componentVersion.published) {
                throw VersionPublishedException(
                    "Version '$buildVersion' of component '$componentName' is published. " +
                        "Unable to delete artifact with ID '$artifactId' for the component version. " +
                        "The version must first be unpublished",
                )
            }
            componentVersionArtifactRepository.findByComponentVersionAndArtifactId(componentVersion, artifactId)?.let {
                if (!dryRun) {
                    applicationEventPublisher.publishEvent(
                        DeleteComponentVersionArtifactEvent(componentName, buildVersion, it.toFullDTO(dockerRegistry)),
                    )
                    componentVersionArtifactRepository.delete(it)
                    if (componentVersionArtifactRepository.findByComponentVersion(componentVersion).isEmpty()) {
                        componentVersionRepository.delete(componentVersion)
                    }
                    // NOTE: Component is displayed in UI even if it has no ComponentVersions - no need to check and clean it
                    log.info("$it deleted")
                }
            }
        }
    }

    @Transactional(readOnly = false)
    override fun uploadAndRegisterComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactCoordinates: ArtifactCoordinatesDTO,
        file: MultipartFile,
        artifactType: ArtifactType,
        failOnAlreadyExists: Boolean,
    ): ArtifactFullDTO {
        componentRepository.lock(componentName.hashCode())
        val release = resolveRelease(componentName, version, artifactType)
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(componentName, release.version)

        if (componentVersion?.published == true && artifactType !in RE_REGISTRABLE_TYPES) {
            val existingComponentVersionArtifact = findRegisteredArtifactWithValidChecksum(componentVersion, artifactCoordinates)
            if (existingComponentVersionArtifact != null && existingComponentVersionArtifact.artifact.sha256 == file.sha256()) {
                return handleAlreadyRegistered(
                    registration = existingComponentVersionArtifact,
                    componentName = componentName,
                    version = release.version,
                    failOnAlreadyExists = failOnAlreadyExists,
                )
            }
            throwVersionPublished(componentVersion, componentName, artifactType)
        }

        val uploadResult = artifactService.uploadReportingChange(
            failOnAlreadyExists = failOnAlreadyExists,
            artifactCoordinates = artifactCoordinates,
            file = file,
        )
        val registrationResult = registerOrGetComponentVersionArtifact(
            componentName = componentName,
            componentVersion = componentVersion,
            version = release.version,
            artifactId = uploadResult.artifact.id,
            artifactType = artifactType,
            failOnAlreadyExists = failOnAlreadyExists,
        )

        if (uploadResult.changed || registrationResult.created) {
            applicationEventPublisher.publishEvent(
                RegisterComponentVersionArtifactEvent(
                    componentName,
                    release.version,
                    registrationResult.artifact,
                ),
            )
        }
        return registrationResult.artifact
    }

    @Transactional(readOnly = false)
    override fun addAndRegisterComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactCoordinates: ArtifactCoordinatesDTO,
        artifactType: ArtifactType,
        failOnAlreadyExists: Boolean,
    ): ArtifactFullDTO {
        componentRepository.lock(componentName.hashCode())
        val release = resolveRelease(componentName, version, artifactType)
        val componentVersion = componentVersionRepository.findByComponentNameAndVersion(componentName, release.version)

        if (componentVersion?.published == true && artifactType !in RE_REGISTRABLE_TYPES) {
            val existingComponentVersionArtifact = findRegisteredArtifactWithValidChecksum(componentVersion, artifactCoordinates)
            if (existingComponentVersionArtifact != null) {
                return handleAlreadyRegistered(
                    registration = existingComponentVersionArtifact,
                    componentName = componentName,
                    version = release.version,
                    failOnAlreadyExists = failOnAlreadyExists,
                )
            }
            throwVersionPublished(componentVersion, componentName, artifactType)
        }

        val addResult = artifactService.addReportingChange(
            failOnAlreadyExists = failOnAlreadyExists,
            artifactCoordinates = artifactCoordinates,
        )
        val registrationResult = registerOrGetComponentVersionArtifact(
            componentName = componentName,
            componentVersion = componentVersion,
            version = release.version,
            artifactId = addResult.artifact.id,
            artifactType = artifactType,
            failOnAlreadyExists = failOnAlreadyExists,
        )

        if (addResult.changed || registrationResult.created) {
            applicationEventPublisher.publishEvent(
                RegisterComponentVersionArtifactEvent(
                    componentName,
                    release.version,
                    registrationResult.artifact,
                ),
            )
        }
        return registrationResult.artifact
    }

    private fun registerOrGetComponentVersionArtifact(
        componentName: String,
        componentVersion: ComponentVersion?,
        version: String,
        artifactId: Long,
        artifactType: ArtifactType,
        failOnAlreadyExists: Boolean,
    ): ComponentVersionArtifactRegistrationResult {
        val artifact = artifactRepository.findById(artifactId).orElseThrow {
            NotFoundException("Artifact with ID '$artifactId' is not found")
        }
        val resolvedComponentVersion = componentVersion ?: getOrCreateComponentVersionEntity(
            componentName = componentName,
            version = version,
        )
        val componentVersionArtifact = componentVersionArtifactRepository.findByComponentVersionAndArtifact(
            componentVersion = resolvedComponentVersion,
            artifact = artifact,
        )
        if (componentVersionArtifact != null) {
            return ComponentVersionArtifactRegistrationResult(
                artifact = handleAlreadyRegistered(
                    registration = componentVersionArtifact,
                    componentName = componentName,
                    version = version,
                    failOnAlreadyExists = failOnAlreadyExists,
                ),
                created = false,
            )
        }
        val newComponentVersionArtifact = componentVersionArtifactRepository.save(
            ComponentVersionArtifact(
                componentVersion = resolvedComponentVersion,
                artifact = artifact,
                type = artifactType,
            ),
        )
        return ComponentVersionArtifactRegistrationResult(
            artifact = newComponentVersionArtifact.toFullDTO(dockerRegistry),
            created = true,
        )
    }

    private fun findRegisteredArtifactWithValidChecksum(
        componentVersion: ComponentVersion,
        artifactCoordinates: ArtifactCoordinatesDTO,
    ): ComponentVersionArtifact? {
        val artifact = artifactRepository.findByPath(artifactCoordinates.toPath()) ?: return null
        val registration = componentVersionArtifactRepository.findByComponentVersionAndArtifact(componentVersion, artifact)
            ?: return null
        throwIfArtifactChecksumChanged(artifact)
        return registration
    }

    private fun resolveRelease(
        componentName: String,
        version: String,
        artifactType: ArtifactType,
    ): BuildFullDTO {
        componentsRegistryService.getExternalExplicitComponentVersion(componentName, version)
        val release = releaseManagementService.getRelease(
            component = componentName,
            version = version,
            includeRc = artifactType != ArtifactType.DISTRIBUTION,
        )
        return release
    }

    private fun getOrCreateComponentVersionEntity(
        componentName: String,
        version: String,
    ): ComponentVersion {
        val component = componentRepository.findByName(componentName)
            ?: componentRepository.save(Component(name = componentName))

        return componentVersionRepository.findByComponentAndVersion(component, version)
            ?: componentVersionRepository.save(
                ComponentVersion(
                    component = component,
                    minorVersion = componentsRegistryService
                        .getDetailedComponentVersion(componentName, version)
                        .minorVersion
                        .version,
                    version = version,
                ),
            )
    }

    private fun getComponentVersionArtifactEntity(
        componentName: String,
        version: String,
        artifactId: Long,
    ): ComponentVersionArtifact {
        componentsRegistryService.getExternalExplicitComponentVersion(componentName, version)
        val buildVersion = releaseManagementService.getRelease(componentName, version, true).version
        return componentVersionArtifactRepository.getByComponentVersionComponentNameAndComponentVersionVersionAndArtifactId(
            componentName,
            buildVersion,
            artifactId,
        )
    }

    private fun handleAlreadyRegistered(
        registration: ComponentVersionArtifact,
        componentName: String,
        version: String,
        failOnAlreadyExists: Boolean,
    ): ArtifactFullDTO {
        val message =
            "Artifact with ID '${registration.artifact.id}' is already registered " +
                "for version '$version' of component '$componentName'"

        if (failOnAlreadyExists) {
            throw ArtifactAlreadyExistsException(message)
        }

        log.info(message)
        return registration.toFullDTO(dockerRegistry)
    }

    private fun throwIfArtifactChecksumChanged(artifact: Artifact) {
        val actualSha256 = storageService
            .get(
                repositoryType = artifact.repositoryType,
                includeStaging = false,
                path = artifact.path,
            ).checksums.sha256
        if (artifact.sha256 != actualSha256) {
            throw ArtifactChecksumChangedException(
                "SHA256 checksum has changed from ${artifact.sha256} to $actualSha256 " +
                    "for artifact with ID '${artifact.id}'",
            )
        }
    }

    private fun throwVersionPublished(
        componentVersion: ComponentVersion,
        componentName: String,
        artifactType: ArtifactType,
    ): Unit =
        throw VersionPublishedException(
            "Version '${componentVersion.version}' of component '$componentName' is published. " +
                "Unable to register '$artifactType' artifact. The version must first be unpublished",
        )

    companion object {
        private val log = LoggerFactory.getLogger(ComponentVersionArtifactServiceImpl::class.java)

        private val RE_REGISTRABLE_TYPES = setOf(ArtifactType.NOTES, ArtifactType.REPORT, ArtifactType.MANUALS)

        private fun MultipartFile.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = input.read(buffer)
                while (read >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read)
                    }
                    read = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun BuildFullDTO.toComponentVersionFullDTO(component: ComponentDTO) =
            ComponentVersionFullDTO(
                component.id,
                version,
                false,
                status,
                hotfix,
                promotedAt,
                component.name,
                component.solution,
                component.clientCode,
                component.parentComponent,
                component.labels,
                limitations,
            )
    }
}
