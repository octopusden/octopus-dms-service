package org.octopusden.octopus.dms.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsStatusesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.legacy.LegacyArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.legacy.LegacyArtifactsDTO
import org.octopusden.octopus.dms.dto.ComponentVersionStatusWithInfoDTO
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.VersionFormatIsNotValidException
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.octopus.dms.service.StorageService
import org.octopusden.octopus.dms.service.impl.ComponentVersionArtifactMapper
import org.octopusden.releng.versions.NumericVersionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Deprecated("")
@RestController
@RequestMapping("/rest/api/2/")
@Tag(name = "Artifact Controller v2 (deprecated)")
class ArtifactControllerV2(
    private val componentService: ComponentService,
    private val componentsRegistryService: ComponentsRegistryService,
    private val componentVersionRepository: ComponentVersionRepository,
    private val componentVersionArtifactRepository: ComponentVersionArtifactRepository,
    private val relengService: ReleaseManagementService,
    private val storageService: StorageService,
    private val componentVersionArtifactMapper: ComponentVersionArtifactMapper
) {
    @Operation(summary = "List of Component Versions")
    @GetMapping("component/{component}/versions", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
                "@permissionEvaluator.hasPermissionByComponent(#component)"
    )
    fun getVersionsWithStatus(
        @Parameter(
            description = "Component name",
            required = true,
            allowEmptyValue = false,
            example = "ee-component"
        )
        @PathVariable(required = true) component: String,
        @Parameter(example = "RELEASE")
        @RequestParam("status", required = false, defaultValue = "RELEASE,RC") status: Array<BuildStatus>,
        @RequestParam("filter-by-minor", required = false, defaultValue = "") minorVersions: List<String>
    ): ComponentVersionsStatusesDTO {
        logger.warn("Deprecated call! V2 getVersionsWithStatus($component, $status, $minorVersions)")
        val componentVersionEntities = if (minorVersions.isEmpty()) {
            componentVersionRepository.findByComponentName(component)
        } else {
            componentVersionRepository.findByComponentNameAndMinorVersions(component, minorVersions)
        }
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        return ComponentVersionsStatusesDTO(
            if (componentVersionEntities.isEmpty()) {
                emptyList()
            } else {
                val componentBuilds = relengService.getComponentBuilds(
                    component,
                    status,
                    componentVersionEntities.map { it.version }.toSet()
                )
                componentVersionEntities.map { cv ->
                    ComponentVersionStatusWithInfoDTO(
                        component,
                        cv.version,
                        componentBuilds.find { it.version == cv.version }?.status ?: BuildStatus.UNKNOWN_STATUS,
                        numericVersionFactory.create(cv.version)
                    )
                }.filter {
                    status.contains(it.status)
                }.sortedWith(compareByDescending { it.versionInfo })
            }
        )
    }

    @Operation(summary = "List of Component Minor Versions")
    @GetMapping("component/{component}/minor-versions")
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
                "@permissionEvaluator.hasPermissionByComponent(#component)"
    )
    fun getMinorVersions(@PathVariable("component") component: String) =
        componentService.getComponentMinorVersions(component).sortedDescending().also {
            logger.warn("Deprecated call! V2 getMinorVersions($component)")
        }

    @Operation(summary = "List of existed Artifacts")
    @GetMapping("component/{component}/version/{version}/{type}/list")
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
                "@permissionEvaluator.hasPermissionByComponent(#component)"
    )
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
    ): String {
        logger.warn("Deprecated call! V2 getList($component, $version, $type)")
        val result = getArtifacts(component, getBuildVersion(component, version), type)
        return mapper.writeValueAsString(result)
    }

    @Operation(summary = "List of Artifacts")
    @GetMapping("component/{component}/version/{version}/artifacts", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
                "@permissionEvaluator.hasPermissionByComponent(#component)"
    )
    @Transactional(readOnly = true)
    fun getKnownComponentVersionArtifacts(
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
        @PathVariable(required = true) version: String
    ): LegacyArtifactsDTO {
        logger.warn("Deprecated call! V2 getKnownComponentVersionArtifacts($component, $version)")
        return LegacyArtifactsDTO(getArtifacts(component, getBuildVersion(component, version), null))
    }

    @Operation(summary = "Get Artifacts repositories")
    @GetMapping("repositories", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRepositories() = storageService.getRepositoriesUrls(RepositoryType.MAVEN, false).also {
        logger.warn("Deprecated call! V2 getRepositories()")
    }

    private fun getArtifacts(component: String, version: String, type: ArtifactType?) =
        if (type == null) {
            componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersion(
                component,
                version
            )
        } else {
            componentVersionArtifactRepository.findByComponentVersionComponentNameAndComponentVersionVersionAndType(
                component,
                version,
                type
            )
        }.filter {
            it.artifact.repositoryType == RepositoryType.MAVEN
        }.map {
            val mavenArtifactDTO = componentVersionArtifactMapper.mapToFullDTO(it) as MavenArtifactFullDTO
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

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ArtifactControllerV2::class.java)
        val mapper = ObjectMapper()
    }
}
