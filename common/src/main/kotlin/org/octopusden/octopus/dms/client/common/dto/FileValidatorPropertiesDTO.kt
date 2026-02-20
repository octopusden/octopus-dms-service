package org.octopusden.octopus.dms.client.common.dto

import java.util.Objects

class FileValidatorPropertiesDTO(
    val rules: Set<FileValidatorRulePropertiesDTO>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileValidatorPropertiesDTO
        return rules == other.rules
    }

    override fun hashCode(): Int = Objects.hash(rules)
}