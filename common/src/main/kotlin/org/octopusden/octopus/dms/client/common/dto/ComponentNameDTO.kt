package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Name of a component")
data class ComponentNameDTO(val componentName: String)