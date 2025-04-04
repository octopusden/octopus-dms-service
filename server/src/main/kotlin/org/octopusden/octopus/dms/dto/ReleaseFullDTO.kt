package org.octopusden.octopus.dms.dto

import java.util.Date
import java.util.Objects
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
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ReleaseFullDTO

        if (promotedAt != other.promotedAt) return false
        if (dependencies != other.dependencies) return false

        return true
    }

    override fun hashCode() = Objects.hash(componentName, buildVersion, status, promotedAt, dependencies)

    override fun toString() = "ReleaseFullDTO(componentName='$componentName', buildVersion='$buildVersion', status=$status, promotedAt=$promotedAt, dependencies=$dependencies)"
}