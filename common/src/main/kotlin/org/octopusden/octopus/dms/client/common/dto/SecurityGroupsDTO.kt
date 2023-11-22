package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Security groups",
    example = "{\n" +
            "  \"read\": [\n" +
            "    \"Production Security\"\n" +
            "  ]\n" +
            "}"
)
data class SecurityGroupsDTO(val read: List<String> = emptyList())
