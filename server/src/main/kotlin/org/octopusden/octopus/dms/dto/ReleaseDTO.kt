package org.octopusden.octopus.dms.dto

import java.util.Objects
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus

open class ReleaseDTO(
    val componentName: String,
    val buildVersion: String,
    val status: ComponentVersionStatus,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReleaseDTO

        if (componentName != other.componentName) return false
        if (buildVersion != other.buildVersion) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode() = Objects.hash(componentName, buildVersion, status)

    override fun toString(): String {
        return "ReleaseDTO(componentName='$componentName', buildVersion='$buildVersion', status=$status)"
    }
}