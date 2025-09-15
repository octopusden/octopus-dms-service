package org.octopusden.octopus.dms.controller

import org.octopusden.octopus.dms.service.ArtifactService
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/rest/api/3/artifacts")
@Tag(name = "Artifact Controller")
class ArtifactController(
    private val artifactService: ArtifactService
) {
    @Operation(summary = "Get Repositories URLs")
    @GetMapping("repositories")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META')")
    fun repositories(
        @Parameter(description = "Repository type") @RequestParam("repository-type") repositoryType: RepositoryType
    ) = artifactService.repositories(repositoryType).sortedDescending()

    @Operation(summary = "Get Artifact")
    @GetMapping("{id}")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META')")
    fun get(
        @Parameter(description = "ID") @PathVariable("id") id: Long
    ) = artifactService.get(id)

    @Operation(summary = "Find Artifact")
    @PostMapping("find")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_META')")
    fun find(
        @RequestBody artifactCoordinates: ArtifactCoordinatesDTO
    ) = artifactService.find(artifactCoordinates)

    @GetMapping(
        "{id}/download",
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE]
    )
    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')")
    fun download(
        @Parameter(description = "ID") @PathVariable("id") id: Long,
        response: HttpServletResponse
    ) = artifactService.download(id).run {
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

    @Operation(summary = "Add Artifact")
    @PostMapping("add")
    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')")
    fun add(
        @Parameter(description = "Fail if artifact is added already") @RequestParam("fail-on-already-exists", defaultValue = "false", required = false) failOnAlreadyExists: Boolean,
        @RequestBody artifactCoordinates: ArtifactCoordinatesDTO
    ) = artifactService.add(failOnAlreadyExists, artifactCoordinates)

    @Operation(summary = "Upload Artifact")
    @PostMapping("upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')") //TODO: separate permission for download?
    fun upload(
        @Parameter(description = "Fail if artifact is uploaded already") @RequestParam("fail-on-already-exists", defaultValue = "false", required = false) failOnAlreadyExists: Boolean,
        @Parameter(schema = Schema(implementation = MavenArtifactCoordinatesDTO::class)) @RequestPart("artifact") artifactCoordinates: ArtifactCoordinatesDTO,
        @Parameter(description = "Artifact file") @RequestPart("file") file: MultipartFile
    ) = artifactService.upload(failOnAlreadyExists, artifactCoordinates, file)
}