package org.octopusden.octopus.dms.client.common.dto

import java.util.Objects

class NameValidatorPropertiesDTO(
    val enabled: Boolean,
    val allowedPattern: Regex
) { //NOTE: cannot use data class, Regex equals method is invalid
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NameValidatorPropertiesDTO

        if (enabled != other.enabled) return false
        if (allowedPattern.pattern != other.allowedPattern.pattern) return false
        if (allowedPattern.options != other.allowedPattern.options) return false

        return true
    }

    override fun hashCode() = Objects.hash(enabled, allowedPattern)
}