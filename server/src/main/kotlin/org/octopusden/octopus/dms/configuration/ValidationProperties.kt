package org.octopusden.octopus.dms.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "validation")
data class ValidationProperties(
    val fileValidator: FileValidatorProperties,
    val nameValidator: NameValidatorProperties,
    val contentValidator: ContentValidatorProperties
) {
    data class FileValidatorProperties(val enabled: Boolean, val rules: Set<FileValidatorRuleProperties>)

    data class FileValidatorRuleProperties(val id: String, val pattern: Regex)

    data class NameValidatorProperties(val enabled: Boolean, val allowedPattern: Regex)

    data class ContentValidatorProperties(
        val enabled: Boolean,
        val parallelism: Int,
        val exclude: Set<String> = emptySet(),
        val forbiddenTokens: Set<String> = emptySet(),
        val forbiddenPatterns: Set<Regex> = emptySet()
    )
}
