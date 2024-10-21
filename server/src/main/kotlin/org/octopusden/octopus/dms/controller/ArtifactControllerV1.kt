package org.octopusden.octopus.dms.controller

import com.google.common.util.concurrent.UncheckedExecutionException
import org.octopusden.octopus.dms.entity.MavenArtifact
import org.octopusden.octopus.dms.exception.GeneralArtifactStoreException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.VersionFormatIsNotValidException
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.dms.service.StorageService
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.VersionsDTO
import org.octopusden.octopus.dms.client.common.dto.legacy.ComponentUrlDTO
import org.octopusden.octopus.dms.client.common.dto.legacy.LegacyArtifactDTO
import org.octopusden.octopus.dms.configuration.StorageProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Deprecated("")
@RestController
@RequestMapping("/rest/api/1/")
@Tag(name = "Artifact Controller v1 (deprecated)")
class ArtifactControllerV1(
    private val componentService: ComponentService,
    private val storageService: StorageService,
    private val componentsRegistryService: ComponentsRegistryService,
    private val relengService: ReleaseManagementService,
    private val componentVersionRepository: ComponentVersionRepository,
    private val componentVersionArtifactRepository: ComponentVersionArtifactRepository,
    private val storageProperties: StorageProperties
) {
    @Operation(summary = "Download Artifact")
    @GetMapping(
        "component/{component}/version/{version}/{type}/{name:.+}",
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("@permissionEvaluator.hasPermissionByArtifactType(#type) or " +
            "@permissionEvaluator.hasPermissionByComponent(#component)")
    @Transactional(readOnly = true)
    fun downloadArtifact(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String,
        @Parameter(
            description = "Component version",
            required = true,
            allowEmptyValue = false,
            example = "1.2.3"
        )
        @PathVariable(required = true) version: String,
        @Parameter(
            description = "Distribution type",
            required = true,
            allowEmptyValue = false,
            example = "notes"
        )
        @PathVariable(required = true) type: ArtifactType,
        @Parameter(
            description = "Artifact name",
            required = true,
            allowEmptyValue = false,
            example = "release_notes"
        )
        @PathVariable(required = true) name: String,
        @RequestParam("classifier", required = false) classifier: String? = null,
        response: HttpServletResponse
    ) {
        val buildVersion = getBuildVersion(component, version)
        val componentVersionArtifactEntity = getArtifact(component, buildVersion, type, name, classifier)
        response.contentType = when {
            arrayOf(
                ".zip",
                ".jar",
                ".tar"
            ).any { componentVersionArtifactEntity.artifact.fileName.endsWith(it) } -> MediaType.APPLICATION_OCTET_STREAM_VALUE

            arrayOf(
                ".htm",
                ".html"
            ).any { componentVersionArtifactEntity.artifact.fileName.endsWith(it) } -> MediaType.TEXT_HTML_VALUE

            else -> MediaType.TEXT_PLAIN_VALUE
        }
        response.status = 200
        if (response.contentType == MediaType.APPLICATION_OCTET_STREAM_VALUE) {
            response.addHeader(
                "Content-disposition",
                "attachment; filename= " + componentVersionArtifactEntity.artifact.fileName
            )
        }
        storageService.download(componentVersionArtifactEntity.artifact, false).use {
            it.copyTo(response.outputStream)
        }
        response.flushBuffer()
    }

    @Operation(summary = "List of components version")
    @GetMapping("component/{component}/versions")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#component)")
    fun getVersions(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String
    ) = VersionsDTO(
        componentService.getComponentVersions(component, emptyList(), true).map { it.version }.sortedDescending()
    )

    @Operation(summary = "Get component GAV")
    @GetMapping("component/{component}/version/{version}/{type}/{artifact}/gav")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#component)")
    @Transactional(readOnly = true)
    fun getArtifactGAV(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String,
        @Parameter(
            description = "Component version",
            required = true,
            allowEmptyValue = false,
            example = "1.2.3"
        )
        @PathVariable(required = true) version: String,
        @Parameter(
            description = "Distribution type",
            required = true,
            allowEmptyValue = false,
            example = "notes"
        )
        @PathVariable(required = true) type: ArtifactType,
        @Parameter(
            description = "Artifact name",
            required = true,
            allowEmptyValue = false,
            example = "release_notes"
        )
        @PathVariable(required = true) artifact: String,
        @RequestParam("classifier", required = false) classifier: String? = null,
        @Parameter(
            description = "Packaging",
            required = false,
            allowEmptyValue = true,
            example = "txt"
        )
        @RequestParam("packaging", required = false, defaultValue = "") packaging: String,
        @Parameter(description = "Generate or get GAV of existed component", example = "false")
        @RequestParam("generateGAV", required = false) generateGAV: Boolean = false
    ): GavDTO {
        val buildVersion = getBuildVersion(component, version)
        return if (!generateGAV) {
            val artifactDTO = getArtifact(component, buildVersion, type, artifact, classifier).toFullDTO()
            if (artifactDTO.repositoryType == RepositoryType.MAVEN) {
                (artifactDTO as MavenArtifactFullDTO).gav
            } else {
                throw IllegalArgumentException("Repository type '${artifactDTO.repositoryType}' is not supported")
            }
        } else {
            GavDTO("org.octopusden.octopus.distribution.$component.$type", artifact, buildVersion, packaging, classifier)
        }
    }

    @Operation(summary = "Get URL for component")
    @GetMapping("component/{component}/version/{version}/{type}/{artifact}/url")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#component)")
    @Transactional(readOnly = true)
    fun getArtifactUrl(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String,
        @Parameter(
            description = "Component version",
            required = true,
            allowEmptyValue = false,
            example = "1.2.3"
        )
        @PathVariable(required = true) version: String,
        @Parameter(
            description = "Distribution type",
            required = true,
            allowEmptyValue = false,
            example = "notes"
        )
        @PathVariable(required = true) type: ArtifactType,
        @Parameter(
            description = "Artifact name",
            required = true,
            allowEmptyValue = false,
            example = "release_notes"
        )
        @PathVariable(required = true) artifact: String,
        @RequestParam("classifier", required = false) classifier: String?
    ): ComponentUrlDTO {
        val buildVersion = getBuildVersion(component, version)
        val host = storageProperties.artifactory.externalRequestHost ?: storageProperties.artifactory.host
        val componentVersionArtifactEntity = getArtifact(component, buildVersion, type, artifact, classifier)
        val repository = storageService.find(componentVersionArtifactEntity.artifact, false)
        return ComponentUrlDTO("$host/artifactory/$repository${componentVersionArtifactEntity.artifact.path}")
    }

    @Operation(summary = "List of Artifacts")
    @GetMapping("component/{component}/version/{version}/{type}/list")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#component)")
    @Transactional(readOnly = true)
    fun getList(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String,
        @Parameter(
            description = "Component version",
            required = true,
            allowEmptyValue = false,
            example = "1.2.3"
        )
        @PathVariable(required = true) version: String,
        @Parameter(
            description = "Distribution type",
            required = true,
            allowEmptyValue = false,
            example = "notes"
        )
        @PathVariable(required = true) type: ArtifactType
    ): List<String> {
        val buildVersion = getBuildVersion(component, version)
        val result = getArtifacts(component, buildVersion, type).map { it.name }
        logger.debug("Get list of names $component:$buildVersion:$type with result=$result")
        return result
    }

    @Operation(summary = "Previous version of component")
    @GetMapping(
        "component/{component}/version/{version}/{type}/{name}/previousVersion",
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#component)")
    @Transactional(readOnly = true)
    fun getPreviousVersionOfArtifact(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String,
        @Parameter(
            description = "Component version",
            required = true,
            allowEmptyValue = false,
            example = "1.2.3"
        )
        @PathVariable(required = true) version: String,
        @Parameter(
            description = "Distribution type",
            required = true,
            allowEmptyValue = false,
            example = "notes"
        )
        @PathVariable(required = true) type: ArtifactType,
        @PathVariable name: String
    ): String {
        val buildVersion = getBuildVersion(component, version)
        try {
            val versions = componentVersionRepository.findByComponentName(component).filter { componentVersionEntity ->
                componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndType(
                    componentVersionEntity.component.name,
                    componentVersionEntity.version,
                    type
                ).any {
                    (it.artifact.repositoryType == RepositoryType.MAVEN) && (it.artifact as MavenArtifact).artifactId == name.split(
                        "."
                    ).first()
                }
            }.map { it.version }
            val result = componentsRegistryService.findPreviousVersion(component, buildVersion, versions)
            logger.debug("Previous version $component:$buildVersion:$type with result=$result")
            return result
        } catch (e: UncheckedExecutionException) {
            logger.error("Failed to load list of artifacts version", e)
            throw GeneralArtifactStoreException("Failed to load list of artifacts version due: ${e.message}")
        }
    }

    @Operation(summary = "Previous Line Latest versions")
    @GetMapping("component/{component}/version/{version}/previousLinesLatestVersions")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#component)")
    @Transactional(readOnly = true)
    fun getPreviousLines(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String,
        @Parameter(
            description = "Component version",
            required = true,
            allowEmptyValue = false,
            example = "1.2.3"
        )
        @PathVariable(required = true) version: String,
        @RequestParam(value = "includeRc", required = false, defaultValue = "false") includeRc: Boolean
    ): List<String> {
        val buildVersion = getBuildVersion(component, version)
        return try {
            val versions = componentVersionRepository.findByComponentName(component).map { it.version }
            val allowedStatuses = mutableListOf(BuildStatus.RELEASE)
            if (includeRc) {
                allowedStatuses.add(BuildStatus.RC)
            }
            val builds = relengService.getComponentBuilds(
                component,
                allowedStatuses.toTypedArray(),
                versions.toSet()
            )
            componentsRegistryService.findPreviousLines(component, buildVersion, builds.map { it.version })
        } catch (e: UncheckedExecutionException) {
            logger.error("Failed to load list of artifacts version", e)
            throw GeneralArtifactStoreException("Failed to load list of artifacts version due: ${e.message}")
        }
    }

    @Operation(summary = "Get Artifacts repository")
    @GetMapping("repositories", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getArtifactsRepository(): ComponentUrlDTO {
        val host = storageProperties.artifactory.externalRequestHost ?: storageProperties.artifactory.host
        return ComponentUrlDTO(
            "$host/artifactory/${storageProperties.artifactory.uploadRepositories[RepositoryType.MAVEN]!!}"
        )
    }

    @GetMapping("error")
    fun error(request: HttpServletRequest): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(request.getParameter("message") ?: "")
    }

    private fun getBuildVersion(component: String, version: String): String {
        val detailedComponentVersion = componentsRegistryService.getDetailedComponentVersion(component, version)
        return when (version) {
            detailedComponentVersion.buildVersion.version -> {
                version
            }

            detailedComponentVersion.releaseVersion.version -> {
                relengService.getComponentBuilds(
                    component,
                    arrayOf(BuildStatus.RELEASE, BuildStatus.RC),
                    setOf(detailedComponentVersion.releaseVersion.jiraVersion)
                ).firstOrNull()?.version
                    ?: throw NotFoundException("There no release for version '${detailedComponentVersion.releaseVersion.jiraVersion}' of component '$component' in Jira")
            }

            else -> {
                throw VersionFormatIsNotValidException("Version '$version' of component '$component' does not fit RELEASE or BUILD format")
            }
        }
    }

    private fun getArtifact(
        component: String, version: String, type: ArtifactType, artifactId: String, classifier: String?
    ) = try {
        componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndType(component, version, type).first {
            if (it.artifact.repositoryType == RepositoryType.MAVEN) {
                it.artifact as MavenArtifact
                it.artifact.artifactId == artifactId && it.artifact.classifier == classifier
            } else false
        }
    } catch (e: NoSuchElementException) {
        throw NotFoundException("Artifact $component:$version type=${type.type} artifactId=$artifactId" + (classifier?.let { " classifier=$classifier" }
            ?: "") + " not found")
    }

    private fun getArtifacts(component: String, version: String, type: ArtifactType?) =
        if (type == null) {
            componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersion(component, version)
        } else {
            componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndType(component, version, type)
        }.filter {
            it.artifact.repositoryType == RepositoryType.MAVEN
        }.map {
            val mavenArtifactDTO = it.toFullDTO() as MavenArtifactFullDTO
            LegacyArtifactDTO(
                mavenArtifactDTO.id,
                mavenArtifactDTO.repositoryType,
                mavenArtifactDTO.gav.artifactId,
                it.type,
                it.displayName,
                it.artifact.fileName,
                mavenArtifactDTO.gav.version,
                mavenArtifactDTO.gav.packaging,
                mavenArtifactDTO.gav.classifier,
                "ARTIFACTORY"
            )
        }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ArtifactControllerV1::class.java)
    }
}
