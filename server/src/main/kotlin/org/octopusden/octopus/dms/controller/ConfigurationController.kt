package org.octopusden.octopus.dms.controller

import org.octopusden.octopus.dms.client.common.dto.ContentValidatorPropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.FileValidatorPropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.NameValidatorPropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.PropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO
import org.octopusden.octopus.dms.configuration.StorageProperties
import org.octopusden.octopus.dms.configuration.ValidationProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.octopusden.octopus.dms.client.common.dto.FileValidatorRulePropertiesDTO
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("rest/api/3/configuration")
@Tag(name = "Configuration Controller")
class ConfigurationController(
    private val storageProperties: StorageProperties,
    private val validationProperties: ValidationProperties
) {
    @Operation(summary = "Get configuration")
    @GetMapping("")
    @PreAuthorize("@permissionEvaluator.hasPermission('ACCESS_CONFIGURATION')")
    fun getConfiguration() = PropertiesDTO(
        storageProperties.mavenGroupPrefix,
        ValidationPropertiesDTO(
            FileValidatorPropertiesDTO(
                validationProperties.fileValidator.enabled,
                validationProperties.fileValidator.rules
                    .map { FileValidatorRulePropertiesDTO(it.id, it.pattern) }.toSet()
            ),
            NameValidatorPropertiesDTO(
                validationProperties.nameValidator.enabled,
                validationProperties.nameValidator.allowedPattern
            ),
            ContentValidatorPropertiesDTO(
                validationProperties.contentValidator.enabled,
                validationProperties.contentValidator.parallelism,
                validationProperties.contentValidator.exclude.toList(),
                validationProperties.contentValidator.forbiddenTokens.toList(),
                validationProperties.contentValidator.forbiddenPatterns.toList()
            )
        )
    )
}