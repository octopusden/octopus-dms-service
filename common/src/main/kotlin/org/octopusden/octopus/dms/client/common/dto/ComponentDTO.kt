package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Component info",
    example = "{\n" +
            "  \"id\": \"ee-component-client-specific\",\n" +
            "  \"name\": \"EE Component Client Specific\",\n" +
            "  \"clientCode\": \"CLIENT_CODE\",\n" +
            "  \"parentComponent\": \"ee-component\",\n" +
            "  \"securityGroups\": {\n" +
            "    \"read\": [\n" +
            "      \"Production Security\"\n" +
            "    ]\n" +
            "  }\n" +
            "}"
)
data class ComponentDTO(
    val id: String,
    val name: String,
    val clientCode: String?,
    val parentComponent: String?,
    val securityGroups: SecurityGroupsDTO
)
