package org.octopusden.octopus.dms.controller

import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsStatusesDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentsDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.VersionsDTO
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.octopusden.octopus.dms.client.common.dto.ComponentNameDTO
import javax.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rest/api/3/components")
@Tag(name = "Component Controller")
class ComponentController(
    private val componentService: ComponentService
) {
    @Operation(summary = "List of Components")
    @GetMapping
    @PostAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.filterComponents(returnObject)")
    fun getComponents() = ComponentsDTO(
        componentService.getComponents()
            .sortedWith(compareBy { it.name })
            .toMutableList() //Required for PostAuthorize
    )

    @Operation(summary = "Delete Component")
    @DeleteMapping("{component-name}")
    @PreAuthorize("@permissionEvaluator.hasPermission('DELETE_DATA')")
    fun deleteComponent(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean
    ) = componentService.deleteComponent(componentName, dryRun)

    @Operation(summary = "Update Component name")
    @PostMapping("{component-name}")
    @PreAuthorize("@permissionEvaluator.hasPermission('DELETE_DATA')")
    fun updateComponent(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @RequestBody newComponentName: ComponentNameDTO
    ): ComponentNameDTO = ComponentNameDTO(componentService.updateComponentName(componentName, newComponentName.componentName))

    @Operation(
        summary = "List of Component Minor Versions",
        description = "Returns list of minor versions that have at least one artifact"
    )
    @GetMapping("{component-name}/minor-versions")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)")
    fun getComponentMinorVersions(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String
    ) = componentService.getComponentMinorVersions(componentName).sortedDescending()

    @Operation(
        summary = "List of Component Versions",
        description = "Returns list of versions that have at least one artifact filtered by build status and minor version"
    )
    @GetMapping("{component-name}/versions")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)")
    fun getComponentVersions(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Minor versions") @RequestParam("filter-by-minor", defaultValue = "", required = false) minorVersions: List<String>,
        @Parameter(description = "Include RC") @RequestParam("include-rc", defaultValue = "true", required = false) includeRc: Boolean
    ) = ComponentVersionsStatusesDTO(
        componentService.getComponentVersions(componentName, minorVersions, includeRc)
            .sortedWith(compareByDescending { it.versionInfo })
    )

    @Operation(summary = "Delete Component Version")
    @DeleteMapping("{component-name}/versions/{version}")
    @PreAuthorize("@permissionEvaluator.hasPermission('DELETE_DATA')")
    fun deleteComponentVersion(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @RequestParam(defaultValue = "true", required = false) dryRun: Boolean
    ) = componentService.deleteComponentVersion(componentName, version, dryRun)

    @Operation(summary = "List of Component Previous Lines Versions")
    @GetMapping("{component-name}/versions/{version}/previous-lines-latest-versions")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)")
    fun getPreviousLinesLatestVersions(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Include RC") @RequestParam("include-rc", defaultValue = "false", required = false) includeRc: Boolean
    ) = VersionsDTO(
        componentService.getPreviousLinesLatestVersions(componentName, version, includeRc).sortedDescending()
    )

    @Operation(summary = "Get list of Component Version Artifacts")
    @GetMapping("{component-name}/versions/{version}/artifacts")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByArtifactType(#type) or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)")
    fun getComponentVersionArtifacts(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact type") @RequestParam("type") type: ArtifactType?
    ) = componentService.getComponentVersionArtifacts(componentName, version, type)

    @Operation(summary = "Get Component Version Artifact by ID")
    @GetMapping("{component-name}/versions/{version}/artifacts/{artifact-id}")
    @PostAuthorize("@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByArtifactType(returnObject.type) or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)")
    fun getComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long
    ) = componentService.getComponentVersionArtifact(componentName, version, artifactId)

    @Operation(
        summary = "Download Component Version Artifact", responses = [ApiResponse(
            responseCode = "200",
            description = "OK",
            content = [
                Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE),
                Content(mediaType = MediaType.TEXT_HTML_VALUE),
                Content(mediaType = MediaType.TEXT_PLAIN_VALUE)
            ]
        )]
    )
    @GetMapping(
        "{component-name}/versions/{version}/artifacts/{artifact-id}/download",
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE]
    )
    @PreAuthorize("@permissionEvaluator.hasPermissionByComponent(#componentName) or " +
            "@permissionEvaluator.hasPermissionByArtifactType(#componentName, #version, #artifactId)")
    fun downloadComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long,
        response: HttpServletResponse
    ) = componentService.downloadComponentVersionArtifact(componentName, version, artifactId).run {
        response.contentType = when {
            arrayOf(".zip", ".jar", ".tar").any { this.fileName.endsWith(it) } -> MediaType.APPLICATION_OCTET_STREAM_VALUE
            arrayOf(".htm", ".html").any { this.fileName.endsWith(it) } -> MediaType.TEXT_HTML_VALUE
            else -> MediaType.TEXT_PLAIN_VALUE
        }
        response.status = 200
        if (response.contentType == MediaType.APPLICATION_OCTET_STREAM_VALUE) {
            response.addHeader("Content-disposition", "attachment; filename= " + this.fileName)
        }
        this.file.use { it.copyTo(response.outputStream) }
        response.flushBuffer()
    }

    @Operation(summary = "Register Component Version Artifact")
    @PostMapping("{component-name}/versions/{version}/artifacts/{artifact-id}")
    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')")
    fun registerComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long,
        @Parameter(description = "Fail if artifact is registered already") @RequestParam("fail-on-already-exists", defaultValue = "false", required = false) failOnAlreadyExists: Boolean,
        @RequestBody registerArtifactDTO: RegisterArtifactDTO
    ) = componentService.registerComponentVersionArtifact(componentName, version, artifactId, failOnAlreadyExists, registerArtifactDTO)

    @Operation(summary = "Delete Component Version Artifact")
    @DeleteMapping("{component-name}/versions/{version}/artifacts/{artifact-id}")
    @PreAuthorize("@permissionEvaluator.hasPermission('DELETE_DATA')")
    fun deleteComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long,
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean
    ) = componentService.deleteComponentVersionArtifact(componentName, version, artifactId, dryRun)
}
