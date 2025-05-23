package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactCoordinatesDTO
import org.octopusden.octopus.dms.dto.DownloadArtifactDTO
import org.octopusden.octopus.dms.entity.Artifact
import org.octopusden.octopus.dms.entity.DebianArtifact
import org.octopusden.octopus.dms.entity.DockerArtifact
import org.octopusden.octopus.dms.entity.MavenArtifact
import org.octopusden.octopus.dms.entity.RpmArtifact
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.GeneralArtifactStoreException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.repository.ArtifactRepository
import org.octopusden.octopus.dms.service.ArtifactService
import org.octopusden.octopus.dms.service.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class ArtifactServiceImpl(
    private val storageService: StorageService,
    private val artifactRepository: ArtifactRepository
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
            storageService.download(artifact.repositoryType, true, artifact.path)
        )
    }

    @Transactional(readOnly = false)
    override fun add(
        failOnAlreadyExists: Boolean,
        artifactCoordinates: ArtifactCoordinatesDTO
    ): ArtifactDTO {
        log.info("Add artifact with coordinates '$artifactCoordinates'")
        val sha256 = storageService.get(
            artifactCoordinates.repositoryType, true, artifactCoordinates.toPath()
        ).checksums.sha256
        val artifact = artifactRepository.findByPath(artifactCoordinates.toPath())?.let {
            with("Artifact with coordinates '${it.path}' already added") {
                if (failOnAlreadyExists) throw ArtifactAlreadyExistsException(this)
                log.info(this)
            }
            it.updateSha256(sha256)
        } ?: artifactRepository.save(artifactCoordinates.createArtifact(false, sha256))
        return artifact.toDTO()
    }

    @Transactional(readOnly = false)
    override fun upload(
        failOnAlreadyExists: Boolean,
        artifactCoordinates: ArtifactCoordinatesDTO,
        file: MultipartFile
    ): ArtifactDTO {
        log.info("Upload file ${file.originalFilename} as artifact with coordinates '$artifactCoordinates'")
        val artifact = artifactRepository.findByPath(artifactCoordinates.toPath())?.let {
            with("Artifact '${it.path}' already uploaded") {
                if (failOnAlreadyExists) throw ArtifactAlreadyExistsException(this)
                log.info(this)
            }
            //NOTE:
            // allowing of artifact re-uploading may be an issue if it is registered for published component version
            // but uploading is used for non-distribution artifacts only so it is allowed for repeatable build purpose
            it.updateSha256(
                file.inputStream.use { inputStream ->
                    storageService.upload(it.repositoryType, it.path, inputStream)
                }.checksums.sha256
            )
        } ?: artifactRepository.save(
            artifactCoordinates.createArtifact(
                true, file.inputStream.use { inputStream ->
                    storageService.upload(artifactCoordinates.repositoryType, artifactCoordinates.toPath(), inputStream)
                }.checksums.sha256
            )
        )
        return artifact.toDTO()
    }

    private fun Artifact.updateSha256(sha256: String) = if (this.sha256 != sha256) {
        log.info("SHA256 checksum has changed from ${this.sha256} to $sha256 for artifact with coordinates '${this.path}'")
        this.sha256 = sha256
        artifactRepository.save(this)
    } else this


    private fun ArtifactCoordinatesDTO.createArtifact(uploaded: Boolean, sha256: String) = when (repositoryType) {
        RepositoryType.MAVEN -> {
            this as MavenArtifactCoordinatesDTO
            MavenArtifact(
                uploaded = uploaded,
                path = toPath(),
                sha256 = sha256,
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
                uploaded = uploaded, path = toPath(), sha256 = sha256
            )
        }

        RepositoryType.RPM -> {
            this as RpmArtifactCoordinatesDTO
            RpmArtifact(
                uploaded = uploaded, path = toPath(), sha256 = sha256
            )
        }


        RepositoryType.DOCKER -> {
            this as DockerArtifactCoordinatesDTO
            if (tag.equals("latest", true)) {
                throw GeneralArtifactStoreException("Docker tag '$tag' is forbidden for registration")
            }
            DockerArtifact(
                uploaded = uploaded,
                path = toPath(),
                sha256 = sha256,
                image = image,
                tag = tag
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArtifactServiceImpl::class.java)
    }
}