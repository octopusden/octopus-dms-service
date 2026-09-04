package org.octopusden.octopus.dms.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.service.ComponentVersionArtifactService
import org.slf4j.LoggerFactory
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
@RequestMapping("/rest/api/3/components/{component-name}/versions/{version}/artifacts")
class ComponentVersionArtifactController(
    private val componentVersionArtifactService: ComponentVersionArtifactService
) {
    @Operation(summary = "Get list of Component Version Artifacts")
    @GetMapping
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
                "@permissionEvaluator.hasPermissionByArtifactType(#type) or " +
                "@permissionEvaluator.hasPermissionByComponent(#componentName)",
    )
    fun getComponentVersionArtifacts(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact type") @RequestParam("type") type: ArtifactType?,
    ): ArtifactsDTO {
        log.info("")
        return componentVersionArtifactService.getComponentVersionArtifacts(componentName, version, type)
    }

    @Operation(summary = "Get Component Version Artifact by ID")
    @GetMapping("{artifact-id}")
    @PostAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
                "@permissionEvaluator.hasPermissionByArtifactType(returnObject.type) or " +
                "@permissionEvaluator.hasPermissionByComponent(#componentName)",
    )
    fun getComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long,
    ): ArtifactFullDTO {
        log.info("")
        return componentVersionArtifactService.getComponentVersionArtifact(componentName, version, artifactId)
    }

    @Operation(
        summary = "Download Component Version Artifact",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE),
                    Content(mediaType = MediaType.TEXT_HTML_VALUE),
                    Content(mediaType = MediaType.TEXT_PLAIN_VALUE),
                ],
            ),
        ],
    )
    @GetMapping(
        "{artifact-id}/download",
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE],
    )
    @PreAuthorize(
        "@permissionEvaluator.hasPermissionByComponent(#componentName) or " +
                "@permissionEvaluator.hasPermissionByArtifactType(#componentName, #version, #artifactId)",
    )
    fun downloadComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long,
        response: HttpServletResponse,
    ) {
        log.info("")
        componentVersionArtifactService.downloadComponentVersionArtifact(componentName, version, artifactId).run {
            response.contentType = when {
                arrayOf(
                    ".zip",
                    ".jar",
                    ".tar",
                ).any { this.fileName.endsWith(it) } -> MediaType.APPLICATION_OCTET_STREAM_VALUE

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
    }

    @Operation(summary = "Register Component Version Artifact")
    @PostMapping("{artifact-id}")
    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')")
    fun registerComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long,
        @Parameter(description = "Fail if artifact is registered already") @RequestParam(
            "fail-on-already-exists",
            defaultValue = "false",
            required = false,
        ) failOnAlreadyExists: Boolean,
        @RequestBody registerArtifactDTO: RegisterArtifactDTO,
    ): ArtifactFullDTO {
        log.info("")
        return componentVersionArtifactService.registerComponentVersionArtifact(
            componentName,
            version,
            artifactId,
            failOnAlreadyExists,
            registerArtifactDTO,
        )
    }

    @Operation(summary = "Delete Component Version Artifact")
    @DeleteMapping("{artifact-id}")
    @PreAuthorize("@permissionEvaluator.hasPermission('DELETE_DATA')")
    fun deleteComponentVersionArtifact(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Artifact ID") @PathVariable("artifact-id") artifactId: Long,
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean,
    ) {
        log.info("")
        componentVersionArtifactService.deleteComponentVersionArtifact(
            componentName,
            version,
            artifactId,
            dryRun,
        )
    }

//    @Operation(summary = "Upload artifact and register it for component version")
//    @PostMapping(
//        "{component-name}/versions/{version}/artifacts/upload",
//        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
//    )
//    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')")
//    fun uploadAndRegisterComponentVersionArtifact(
//        @PathVariable("component-name") componentName: String,
//        @PathVariable version: String,
//        @RequestParam("fail-on-already-exists", defaultValue = "false", required = false) failOnAlreadyExists: Boolean,
//        @Parameter(schema = Schema(implementation = ArtifactCoordinatesDTO::class))
//        @RequestPart("artifact") artifactCoordinates: ArtifactCoordinatesDTO,
//        @Parameter(description = "Artifact file")
//        @RequestPart("file") file: MultipartFile,
//        @RequestParam type: ArtifactType,
//    ): ArtifactFullDTO {
//        return componentVersionArtifactService.uploadAndRegisterComponentVersionArtifact(
//            componentName,
//            version,
//            artifactCoordinates,
//            file,
//            type,
//            failOnAlreadyExists,
//        )
//    }
//
//    @Operation(summary = "Add artifact by coordinates and register it for component version")
//    @PostMapping("{component-name}/versions/{version}/artifacts")
//    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')")
//    fun addAndRegisterComponentVersionArtifact(
//        @PathVariable("component-name") componentName: String,
//        @PathVariable version: String,
//        @RequestParam("fail-on-already-exists", defaultValue = "false", required = false) failOnAlreadyExists: Boolean,
//        @RequestBody artifactCoordinates: ArtifactCoordinatesDTO,
//        @RequestParam type: ArtifactType,
//    ): ArtifactFullDTO {
//        return componentVersionArtifactService.addAndRegisterComponentVersionArtifact(
//            componentName,
//            version,
//            artifactCoordinates,
//            type,
//            failOnAlreadyExists,
//        )
//    }

    companion object {
        private val log = LoggerFactory.getLogger(ComponentVersionArtifactController::class.java)
    }
}
