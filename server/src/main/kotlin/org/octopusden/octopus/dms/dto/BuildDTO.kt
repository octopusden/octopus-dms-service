package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus
import java.util.Objects

open class BuildDTO(
    val component: String,
    val version: String,
    val status: ComponentVersionStatus,
    val hotfix: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BuildDTO

        if (component != other.component) return false
        if (version != other.version) return false
        if (status != other.status) return false
        if (hotfix != other.hotfix) return false

        return true
    }

    override fun hashCode() = Objects.hash(component, version, status, hotfix)

    override fun toString(): String = "BuildDTO(component='$component', version='$version', status=$status, hotfix=$hotfix)"
}
