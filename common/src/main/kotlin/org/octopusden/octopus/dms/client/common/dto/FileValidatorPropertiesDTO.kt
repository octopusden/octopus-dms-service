package org.octopusden.octopus.dms.client.common.dto

import java.util.Objects

class FileValidatorPropertiesDTO(
    val enabled: Boolean,
    val requiredPatterns: Set<Regex>,
) { //NOTE: cannot use data class, Regex equals method is invalid
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileValidatorPropertiesDTO

        if (enabled != other.enabled) return false

        if (this.requiredPatterns.map { it.pattern }.toSet() != other.requiredPatterns.map { it.pattern }.toSet()) return false
        if (this.requiredPatterns.map { it.options }.toSet() != other.requiredPatterns.map { it.options }.toSet()) return false

        return true
    }

    override fun hashCode() = Objects.hash(enabled, requiredPatterns.map { it.pattern }.toSet())
}
