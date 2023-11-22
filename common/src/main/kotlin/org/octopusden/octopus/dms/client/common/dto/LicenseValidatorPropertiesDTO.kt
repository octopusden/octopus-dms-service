package org.octopusden.octopus.dms.client.common.dto

class LicenseValidatorPropertiesDTO(
    val enabled: Boolean,
    val pattern: Regex
) { //NOTE: cannot use data class, Regex equals method is invalid
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LicenseValidatorPropertiesDTO) return false
        if (enabled != other.enabled) return false
        if (pattern.pattern != other.pattern.pattern) return false
        if (pattern.options != other.pattern.options) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + pattern.hashCode()
        return result
    }
}
