package org.octopusden.octopus.dms.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.octopusden.octopus.dms.service.AdminService
import org.octopusden.octopus.dms.util.sanitizeForLog
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rest/api/3/admin")
@Tag(name = "Admin Controller")
@PreAuthorize("@permissionEvaluator.hasPermission('DELETE_DATA')")
class AdminController(
    private val adminService: AdminService,
) {
    @Operation(summary = "Delete Invalid Components")
    @DeleteMapping("invalid-components")
    fun deleteInvalidComponents(
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean,
    ) {
        log.info("Delete invalid components: dryRun='{}'", dryRun)
        adminService.deleteInvalidComponents(dryRun)
    }

    @Operation(summary = "Delete Invalid Components Versions")
    @DeleteMapping("invalid-components-versions")
    fun deleteInvalidComponentsVersions(
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean,
    ) {
        log.info("Delete invalid components versions: dryRun='{}'", dryRun)
        adminService.deleteInvalidComponentsVersions(dryRun)
    }

    @Operation(summary = "Delete Invalid Artifacts")
    @DeleteMapping("invalid-artifacts")
    fun deleteInvalidArtifacts(
        @RequestParam("update-sha256", defaultValue = "true", required = false) updateSha256: Boolean,
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean,
    ) {
        log.info("Delete invalid artifacts: updateSha256='{}', dryRun='{}'", updateSha256, dryRun)
        adminService.deleteInvalidArtifacts(updateSha256, dryRun)
    }

    @Operation(summary = "Delete Orphaned Artifacts")
    @DeleteMapping("orphaned-artifacts")
    fun deleteOrphanedArtifacts(
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean,
    ) {
        log.info("Delete orphaned artifacts: dryRun='{}'", dryRun)
        adminService.deleteOrphanedArtifacts(dryRun)
    }

    @Operation(summary = "Rename a component")
    @PostMapping("rename-component/{component-name}/{new-component-name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun renameComponent(
        @PathVariable(name = "component-name") componentName: String,
        @PathVariable(name = "new-component-name") newComponentName: String,
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean,
    ) {
        log.info(
            "Rename component: componentName='{}', newComponentName='{}', dryRun='{}'",
            componentName.sanitizeForLog(),
            newComponentName.sanitizeForLog(),
            dryRun,
        )
        adminService.renameComponent(componentName, newComponentName, dryRun)
    }

    @PostMapping("archive-component/{component-name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archiveComponent(
        @PathVariable("component-name") componentName: String,
    ) {
        log.info("Archive component: component='{}'", componentName.sanitizeForLog())
        adminService.archiveComponent(componentName)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminController::class.java)
    }
}
