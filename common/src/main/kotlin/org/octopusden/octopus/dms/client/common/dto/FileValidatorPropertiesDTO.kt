package org.octopusden.octopus.dms.client.common.dto

import java.util.Objects

class FileValidatorPropertiesDTO(
    val enabled: Boolean,
    val requiredPatterns: Set<Regex>,
) { //NOTE: cannot use data class, Regex equals method is invalid
    private fun normalized(): Set<Pair<String, Set<RegexOption>>> =
        requiredPatterns.map { it.pattern to it.options }.toSet()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileValidatorPropertiesDTO
        if (enabled != other.enabled) return false
        return normalized() == other.normalized()
    }

    override fun hashCode(): Int =
        Objects.hash(enabled, normalized())
}
