package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactCoordinatesDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.entity.DebianArtifact
import org.octopusden.octopus.dms.entity.DockerArtifact
import org.octopusden.octopus.dms.entity.MavenArtifact
import org.octopusden.octopus.dms.entity.RpmArtifact
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.GeneralArtifactStoreException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.service.ArtifactService
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class ArtifactServiceImpl(
    private val storageService: StorageService,
    private val artifactRepository: ArtifactRepository,
    private val componentService: ComponentService
) : ArtifactService {
    override fun repositories(repositoryType: RepositoryType): List<String> {
        log.info("Get $repositoryType repositories")
        return storageService.getRepositoriesUrls(repositoryType, false)
    }

    @Transactional(readOnly = true)
    override fun get(id: Long): ArtifactDTO {
        log.info("Get artifact with ID '$id'")
        return artifactRepository.findById(id)
            .orElseThrow { NotFoundException("Artifact with ID '$id' is not found") }
            .toDTO()
    }

    @Transactional(readOnly = true)
    override fun find(artifactCoordinates: ArtifactCoordinatesDTO): ArtifactDTO {
        log.info("Find artifact with coordinates '$artifactCoordinates'")
        return (artifactRepository.findByPath(artifactCoordinates.toPath())
            ?: throw NotFoundException("Artifact with path '${artifactCoordinates.toPath()}' has not been found")
                ).toDTO()
    }

    @Transactional(readOnly = true)
    override fun download(id: Long): DownloadArtifactDTO {
        log.info("Download artifact with ID '$id'")
        val artifact = artifactRepository.findById(id)
            .orElseThrow { NotFoundException("Artifact with ID '$id' is not found") }
        return DownloadArtifactDTO(
            artifact.fileName,
            storageService.download(artifact, true)
        )
    }

    @Transactional(readOnly = false)
    override fun add(
        failOnAlreadyExists: Boolean,
        artifactCoordinates: ArtifactCoordinatesDTO
    ): ArtifactDTO {
        log.info("Add artifact with coordinates '$artifactCoordinates'")
        val artifact = artifactCoordinates.createArtifact(false)
        storageService.find(artifact, true)
        return (artifactRepository.findByPath(artifact.path)?.let {
            with("Artifact with coordinates '${artifactCoordinates.toPath()}' already added") {
                if (failOnAlreadyExists) {
                    throw ArtifactAlreadyExistsException(this)
                }
                log.info(this)
            }
            it
        } ?: artifactRepository.save(artifact)).toDTO()
    }

    @Transactional(readOnly = false)
    override fun upload(
        failOnAlreadyExists: Boolean,
        artifactCoordinates: ArtifactCoordinatesDTO,
        file: MultipartFile
    ): ArtifactDTO {
        log.info("Upload file ${file.originalFilename} as artifact with coordinates '$artifactCoordinates'")
        val artifact = artifactCoordinates.createArtifact(true).run {
            artifactRepository.findByPath(this.path)?.let {
                with("Artifact '${this.path}' already uploaded") {
                    if (failOnAlreadyExists) {
                        throw ArtifactAlreadyExistsException(this)
                    }
                    log.info(this)
                }
                it
            } ?: artifactRepository.save(this)
        }
        file.inputStream.use { storageService.upload(artifact, it) }
        return artifact.toDTO()
    }

    @Transactional(readOnly = false)
    override fun delete(id: Long, dryRun: Boolean) {
        log.info("Delete artifact with ID '$id'")
        artifactRepository.findById(id).ifPresent { artifact ->
            if (!dryRun) {
                componentService.deleteArtifact(artifact)
            }
            log.info("$artifact deleted")
        }
    }


    private fun ArtifactCoordinatesDTO.createArtifact(uploaded: Boolean) = when (repositoryType) {
        RepositoryType.MAVEN -> {
            this as MavenArtifactCoordinatesDTO
            MavenArtifact(
                uploaded = uploaded,
                path = toPath(),
                groupId = gav.groupId,
                artifactId = gav.artifactId,
                version = gav.version,
                packaging = gav.packaging,
                classifier = gav.classifier
            )
        }

        RepositoryType.DEBIAN -> {
            this as DebianArtifactCoordinatesDTO
            DebianArtifact(
                uploaded = uploaded, path = toPath()
            )
        }

        RepositoryType.RPM -> {
            this as RpmArtifactCoordinatesDTO
            RpmArtifact(
                uploaded = uploaded, path = toPath()
            )
        }


        RepositoryType.DOCKER -> {
            this as DockerArtifactCoordinatesDTO
            if (tag.equals("latest", true)) {
                throw GeneralArtifactStoreException("Docker tag 'latest' is forbidden for registration")
            }
            DockerArtifact(
                uploaded = uploaded,
                path = toPath(),
                image = image,
                tag = tag
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArtifactServiceImpl::class.java)
    }
}