package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus
import java.util.Date
import java.util.Objects

class BuildFullDTO(
    component: String,
    version: String,
    status: ComponentVersionStatus,
    hotfix: Boolean,
    val promotedAt: Date?,
    val parents: List<BuildDTO>,
    val dependencies: List<BuildDTO>,
    val limitations: String? = null,
) : BuildDTO(component, version, status, hotfix) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as BuildFullDTO

        if (promotedAt != other.promotedAt) return false
        if (parents != other.parents) return false
        if (dependencies != other.dependencies) return false
        if (limitations != other.limitations) return false

        return true
    }

    override fun hashCode() = Objects.hash(super.hashCode(), promotedAt, parents, dependencies, limitations)

    override fun toString() =
        "BuildFullDTO(component='$component', version='$version', status=$status, hotfix=$hotfix, promotedAt=$promotedAt, parents=$parents, dependencies=$dependencies, limitations=$limitations)"
}
