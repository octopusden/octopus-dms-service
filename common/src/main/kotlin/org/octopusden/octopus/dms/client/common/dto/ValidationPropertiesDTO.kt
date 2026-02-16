package org.octopusden.octopus.dms.client.common.dto

data class ValidationPropertiesDTO(
    val fileValidation: FileValidatorPropertiesDTO,
    val nameValidation: NameValidatorPropertiesDTO,
    val contentValidation: ContentValidatorPropertiesDTO
)
