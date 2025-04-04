package org.octopusden.octopus.dms.dto

import java.util.Date
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus

class ReleaseFullDTO(
    componentName: String,
    buildVersion: String,
    status: ComponentVersionStatus,
    val promotedAt: Date?,
    val dependencies: List<DependencyDTO>
) : ReleaseDTO(componentName, buildVersion, status) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReleaseFullDTO) return false
        if (!super.equals(other)) return false
        if (promotedAt != other.promotedAt) return false
        if (dependencies != other.dependencies) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (promotedAt?.hashCode() ?: 0)
        result = 31 * result + dependencies.hashCode()
        return result
    }

    override fun toString(): String {
        return "ReleaseFullDTO(componentName='$componentName', buildVersion='$buildVersion', status=$status, promotedAt=$promotedAt, dependencies=$dependencies)"
    }
}