package org.octopusden.octopus.dms.client.common.dto

class NameValidatorPropertiesDTO(
    val enabled: Boolean,
    val allowedPattern: Regex
) { //NOTE: cannot use data class, Regex equals method is invalid
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NameValidatorPropertiesDTO) return false
        if (enabled != other.enabled) return false
        if (allowedPattern.pattern != other.allowedPattern.pattern) return false
        if (allowedPattern.options != other.allowedPattern.options) return false
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + allowedPattern.hashCode()
        return result
    }
}