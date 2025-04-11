package org.octopusden.octopus.dms.security

import org.octopusden.cloud.commons.security.BasePermissionEvaluator
import org.octopusden.cloud.commons.security.SecurityService
import org.octopusden.cloud.commons.security.dto.User
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ComponentsDTO
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PermissionEvaluator(
    private val componentsRegistryService: ComponentsRegistryService,
    private val componentService : ComponentService,
    securityService: SecurityService
) : BasePermissionEvaluator(securityService) {
    fun hasPermissionByArtifactType(type: ArtifactType?) = type?.let {
        when (type) {
            ArtifactType.NOTES, ArtifactType.REPORT -> hasPermission("ACCESS_NOTES")
            ArtifactType.MANUALS -> hasPermission("ACCESS_DOCUMENTATION")
            ArtifactType.DISTRIBUTION -> hasPermission("ACCESS_DISTRIBUTION")
            ArtifactType.STATIC -> false
        }
    } ?: false

    fun hasPermissionByArtifactType(
        componentName: String,
        version: String,
        artifactId: Long
    ) = hasPermissionByArtifactType(
        try {
            componentService.getComponentVersionArtifact(componentName, version, artifactId).type
        } catch(_: Exception) {
            null
        }
    )

    fun hasPermissionByComponent(componentName: String) = hasGroup(
        securityService.getCurrentUser(),
        componentName,
        try {
            componentsRegistryService.getExternalComponent(componentName).securityGroups.read
        } catch (e: Exception) {
            log.warn("Unable to get read security groups for component '$componentName'", e)
            emptyList()
        }
    )

    fun filterComponents(components: ComponentsDTO): Boolean {
        val user = securityService.getCurrentUser()
        val iterator = components.components.iterator()
        while (iterator.hasNext()) {
            val component = iterator.next()
            if (!hasGroup(user, component.name, component.securityGroups.read)) {
                iterator.remove()
            }
        }
        return true
    }

    private fun hasGroup(user: User, componentName: String, readSecurityGroups: List<String>): Boolean {
        if (log.isTraceEnabled) {
            log.trace("Check component '$componentName' read security groups $readSecurityGroups in '${user.username}' groups ${user.groups}'")
        }
        val group = readSecurityGroups.find { it in user.groups }
        return (group != null).also {
            if (log.isDebugEnabled) {
                log.debug("User '${user.username}' was${if (it) "" else " not"} granted to access component '$componentName' by group '$group'")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PermissionEvaluator::class.java)
    }
}
