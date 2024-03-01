package org.octopusden.octopus.dms.controller

import org.octopusden.octopus.dms.service.AdminService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rest/api/3/admin")
@Tag(name = "Admin Controller")
@PreAuthorize("@permissionEvaluator.hasPermission('DELETE_DATA')")
class AdminController(
    private val adminService: AdminService
) {
    @Operation(summary = "Delete Invalid Components")
    @DeleteMapping("invalid-components")
    fun deleteInvalidComponents(
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean
    ) = adminService.deleteInvalidComponents(dryRun)

    @Operation(summary = "Delete Invalid Components Versions")
    @DeleteMapping("invalid-components-versions")
    fun deleteInvalidComponentsVersions(
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean
    ) = adminService.deleteInvalidComponentsVersions(dryRun)

    @Operation(summary = "Recalculate Minor Versions")
    @PostMapping("recalculate-minor-versions")
    fun recalculateMinorVersions(
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean
    ) = adminService.recalculateMinorVersions(dryRun)

    @Operation(summary = "Delete Orphaned Artifacts")
    @DeleteMapping("orphaned-artifacts")
    fun deleteOrphanedArtifacts(
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean
    ) = adminService.deleteOrphanedArtifacts(dryRun)

    @Operation(summary = "Rename a component")
    @PostMapping("rename-component/{component-name}/{new-component-name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun renameComponent(
        @PathVariable(name = "component-name") componentName: String,
        @PathVariable(name = "new-component-name") newComponentName: String,
        @RequestParam("dry-run", defaultValue = "true", required = false) dryRun: Boolean,
    ) = adminService.renameComponent(componentName, newComponentName, dryRun)

}