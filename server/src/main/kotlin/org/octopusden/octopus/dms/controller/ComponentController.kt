package org.octopusden.octopus.dms.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentsDTO
import org.octopusden.octopus.dms.client.common.dto.PatchComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.VersionsDTO
import org.octopusden.octopus.dms.service.ComponentService
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rest/api/3/components")
@Tag(name = "Component Controller")
class ComponentController(
    private val componentService: ComponentService,
) {
    @Operation(summary = "List of Components")
    @GetMapping
    @PostAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.filterComponents(returnObject)",
    )
    fun getComponents(filter: ComponentRequestFilter): ComponentsDTO {
        log.info("Get components: filter={}", filter)
        return ComponentsDTO(
            componentService
                .getComponents(filter)
                .sortedWith(compareBy { it.name })
                .toMutableList(), // Required for PostAuthorize
        )
    }

    @Operation(
        summary = "List of Component Minor Versions",
        description = "Returns list of minor versions that have at least one artifact",
    )
    @GetMapping("{component-name}/minor-versions")
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)",
    )
    fun getComponentMinorVersions(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
    ): List<String> {
        log.info("Get component minor version: componentName={}", componentName)
        return componentService.getComponentMinorVersions(componentName).sortedDescending()
    }

    @Operation(
        summary = "List of Component Versions",
        description = "Returns list of versions that have at least one artifact filtered by build status and minor version",
    )
    @GetMapping("{component-name}/versions")
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)",
    )
    fun getComponentVersions(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Minor versions") @RequestParam(
            "filter-by-minor",
            defaultValue = "",
            required = false,
        ) minorVersions: List<String>,
        @Parameter(description = "Include RC") @RequestParam(
            "include-rc",
            defaultValue = "true",
            required = false,
        ) includeRc: Boolean,
    ): ComponentVersionsDTO {
        log.info("TODO")
        return ComponentVersionsDTO(
            componentService
                .getComponentVersionsWithInfo(componentName, minorVersions, includeRc)
                .sortedWith(compareByDescending { it.versionInfo })
                .map { it.version },
        )
    }

    @GetMapping("{component-name}/versions/{version}/dependencies")
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)",
    )
    fun getComponentVersionDependencies(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
    ): List<ComponentVersionDTO> {
        log.info("TODO")
        return componentService
            .getComponentVersionDependencies(componentName, version)
            .sortedWith(compareBy({ it.version.component }, { it.versionInfo }))
            .map { it.version }
    }

    @PatchMapping("{component-name}/versions/{version}")
    @PreAuthorize("@permissionEvaluator.hasPermission('PUBLISH_ARTIFACT')")
    fun patchComponentVersion(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @RequestBody patchComponentVersionDTO: PatchComponentVersionDTO,
    ): ComponentVersionDTO {
        log.info("TODO")
        return componentService.patchComponentVersion(componentName, version, patchComponentVersionDTO)
    }

    @Operation(summary = "List of Component Previous Lines Versions")
    @GetMapping("{component-name}/versions/{version}/previous-lines-latest-versions")
    @PreAuthorize(
        "@permissionEvaluator.hasPermission('ACCESS_META') or " +
            "@permissionEvaluator.hasPermissionByComponent(#componentName)",
    )
    fun getPreviousLinesLatestVersions(
        @Parameter(description = "Component name") @PathVariable("component-name") componentName: String,
        @Parameter(description = "Build version") @PathVariable("version") version: String,
        @Parameter(description = "Include RC") @RequestParam(
            "include-rc",
            defaultValue = "false",
            required = false,
        ) includeRc: Boolean,
    ): VersionsDTO {
        log.info("TODO")
        return VersionsDTO(
            componentService.getPreviousLinesLatestVersions(componentName, version, includeRc).sortedDescending(),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ComponentController::class.java)
    }
}
