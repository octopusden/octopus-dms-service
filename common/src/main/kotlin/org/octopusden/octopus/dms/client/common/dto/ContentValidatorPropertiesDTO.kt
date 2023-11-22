package org.octopusden.octopus.dms.client.common.dto

class ContentValidatorPropertiesDTO(
    val enabled: Boolean,
    val parallelism: Int,
    val exclude: List<String>,
    val forbiddenTokens: List<String>,
    val forbiddenPatterns: List<Regex>
) { //NOTE: cannot use data class, Regex equals method is invalid
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContentValidatorPropertiesDTO) return false
        if (enabled != other.enabled) return false
        if (parallelism != other.parallelism) return false
        if (exclude != other.exclude) return false
        if (forbiddenTokens != other.forbiddenTokens) return false
        if (forbiddenPatterns.size != other.forbiddenPatterns.size) return false
        for (i in forbiddenPatterns.indices) {
            if (forbiddenPatterns[i].pattern != forbiddenPatterns[i].pattern) return false
            if (forbiddenPatterns[i].options != forbiddenPatterns[i].options) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + parallelism
        result = 31 * result + exclude.hashCode()
        result = 31 * result + forbiddenTokens.hashCode()
        result = 31 * result + forbiddenPatterns.hashCode()
        return result
    }
}