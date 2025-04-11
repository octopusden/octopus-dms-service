package org.octopusden.octopus.dms.client.common.dto

import java.util.Objects

class ContentValidatorPropertiesDTO(
    val enabled: Boolean,
    val parallelism: Int,
    val exclude: List<String>,
    val forbiddenTokens: List<String>,
    val forbiddenPatterns: List<Regex>
) { //NOTE: cannot use data class, Regex equals method is invalid
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContentValidatorPropertiesDTO

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

    override fun hashCode() = Objects.hash(enabled, parallelism, exclude, forbiddenTokens, forbiddenPatterns)
}