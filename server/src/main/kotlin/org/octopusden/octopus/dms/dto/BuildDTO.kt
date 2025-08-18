package org.octopusden.octopus.dms.dto

import java.util.Objects
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus

open class BuildDTO(
    val component: String,
    val version: String,
    val status: ComponentVersionStatus,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BuildDTO

        if (component != other.component) return false
        if (version != other.version) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode() = Objects.hash(component, version, status)

    override fun toString(): String {
        return "BuildDTO(component='$component', version='$version', status=$status)"
    }
}