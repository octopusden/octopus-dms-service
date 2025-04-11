package org.octopusden.octopus.dms.client.common.dto

import java.util.Objects

class LicenseValidatorPropertiesDTO(
    val enabled: Boolean,
    val pattern: Regex
) { //NOTE: cannot use data class, Regex equals method is invalid
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LicenseValidatorPropertiesDTO

        if (enabled != other.enabled) return false
        if (pattern.pattern != other.pattern.pattern) return false
        if (pattern.options != other.pattern.options) return false

        return true
    }

    override fun hashCode() = Objects.hash(enabled, pattern)
}
