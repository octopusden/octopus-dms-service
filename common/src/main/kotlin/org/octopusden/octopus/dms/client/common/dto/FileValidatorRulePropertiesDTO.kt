package org.octopusden.octopus.dms.client.common.dto

import java.util.Objects

class FileValidatorRulePropertiesDTO(
    val id: String,
    val pattern: Regex
) {
    private fun normalizedPattern(): Pair<String, Set<RegexOption>> = pattern.pattern to pattern.options

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileValidatorRulePropertiesDTO
        if (id != other.id) return false
        return normalizedPattern() == other.normalizedPattern()
    }

    override fun hashCode(): Int = Objects.hash(id, normalizedPattern())
}
