package org.octopusden.octopus.dms.dto

import java.util.Date
import java.util.Objects
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus

class BuildFullDTO(
    component: String,
    version: String,
    status: ComponentVersionStatus,
    hotfix: Boolean,
    val promotedAt: Date?,
    val parents: List<BuildDTO>,
    val dependencies: List<BuildDTO>
) : BuildDTO(component, version, status, hotfix) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as BuildFullDTO

        if (promotedAt != other.promotedAt) return false
        if (parents != other.parents) return false
        if (dependencies != other.dependencies) return false

        return true
    }

    override fun hashCode() = Objects.hash(super.hashCode(), promotedAt, parents, dependencies)

    override fun toString() = "BuildFullDTO(component='$component', version='$version', status=$status, hotfix=$hotfix, promotedAt=$promotedAt, parents=$parents, dependencies=$dependencies)"
}