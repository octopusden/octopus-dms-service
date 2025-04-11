package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Component version patch options",
    example = "{\n  \"published\": true\n}"
)
data class PatchComponentVersionDTO(val published: Boolean)
