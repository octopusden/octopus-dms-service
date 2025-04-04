package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus

open class ReleaseDTO(
    val componentName: String,
    val buildVersion: String,
    val status: ComponentVersionStatus,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReleaseDTO) return false
        if (componentName != other.componentName) return false
        if (buildVersion != other.buildVersion) return false
        if (status != other.status) return false
        return true
    }

    override fun hashCode(): Int {
        var result = componentName.hashCode()
        result = 31 * result + buildVersion.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

    override fun toString() = "ReleaseDTO(componentName='$componentName', buildVersion='$buildVersion', status=$status)"
}