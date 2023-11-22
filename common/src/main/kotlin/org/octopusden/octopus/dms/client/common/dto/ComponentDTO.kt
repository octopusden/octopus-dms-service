package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Component info",
    example = "{\n" +
            "  \"id\": \"ee-component\",\n" +
            "  \"name\": \"EE Component\",\n" +
            "  \"securityGroups\": {\n" +
            "    \"read\": [\n" +
            "      \"Production Security\"\n" +
            "    ]\n" +
            "  }\n" +
            "}"
)
data class ComponentDTO(val id: String, val name: String, val securityGroups: SecurityGroupsDTO)
