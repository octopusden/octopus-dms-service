package org.octopusden.octopus.dms.dto

import java.util.Date
import java.util.Objects
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus

class ReleaseFullDTO(
    component: String,
    version: String,
    status: ComponentVersionStatus,
    val promotedAt: Date?,
    val parents: List<BuildDTO>,
    val dependencies: List<BuildDTO>
) : ReleaseDTO(component, version, status) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ReleaseFullDTO

        if (promotedAt != other.promotedAt) return false
        if (parents != other.parents) return false
        if (dependencies != other.dependencies) return false

        return true
    }

    override fun hashCode() = Objects.hash(super.hashCode(), promotedAt, parents, dependencies)

    override fun toString() = "ReleaseFullDTO(component='$component', version='$version', status=$status, promotedAt=$promotedAt, parents=$parents, dependencies=$dependencies)"
}