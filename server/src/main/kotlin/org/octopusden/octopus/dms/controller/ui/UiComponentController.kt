package org.octopusden.octopus.dms.controller.ui

import org.octopusden.octopus.dms.controller.ui.dto.UiComponentsDTO
import org.octopusden.octopus.dms.controller.ui.dto.GroupedComponentVersionsDTO
import org.octopusden.cloud.commons.security.SecurityService
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.service.ComponentService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("ui/components")
class UiComponentController(
    private val componentService: ComponentService,
    private val securityService: SecurityService
) {

    val hiddenStatusTypes = listOf(BuildStatus.BUILD)

    @GetMapping
    fun getComponents(): UiComponentsDTO {
        val user = securityService.getCurrentUser()
        val currentUserGroups = user.groups
        val hasAccessMetaPermission = user.roles.flatMap { it.permissions }.contains("ACCESS_META")

        val components = componentService.getComponents()
            .filter { component ->
                (hasAccessMetaPermission || component
                    .securityGroups
                    .read
                    .any { group -> currentUserGroups.contains(group) } )
            }
            .sortedBy { it.name }
            .associate { it.id to mapOf<String, Any>("name" to it.name) }
        return UiComponentsDTO(components)
    }

    @GetMapping("{component}/minor-versions", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMinorVersions(@PathVariable("component") component: String): List<String> =
        componentService.getComponentMinorVersions(component).sortedDescending()

    @GetMapping("{component}/minor-versions/{minorVersion}/versions", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getGroupedComponentVersions(
        @PathVariable("component") component: String,
        @PathVariable("minorVersion") minorVersion: String,
        @RequestParam("includeRc", defaultValue = "true") includeRc: Boolean,
    ): GroupedComponentVersionsDTO {
        val filteredVersions = componentService.getComponentVersions(component, listOf(minorVersion), includeRc)
            .filter { !hiddenStatusTypes.contains(it.status) }
        return  GroupedComponentVersionsDTO("", filteredVersions, emptyList())
    }
}
