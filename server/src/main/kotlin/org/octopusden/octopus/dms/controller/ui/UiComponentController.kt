package org.octopusden.octopus.dms.controller.ui

import org.octopusden.cloud.commons.security.SecurityService
import org.octopusden.octopus.dms.controller.ui.dto.UiComponentsDTO
import org.octopusden.octopus.dms.service.ComponentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("ui/components")
class UiComponentController(
    private val componentService: ComponentService,
    private val securityService: SecurityService
) {

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
}
