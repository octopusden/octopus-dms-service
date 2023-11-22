package org.octopusden.octopus.dms.client.common.dto

data class ValidationPropertiesDTO(
    val licenseValidation: LicenseValidatorPropertiesDTO,
    val nameValidation: NameValidatorPropertiesDTO,
    val contentValidation: ContentValidatorPropertiesDTO
)
