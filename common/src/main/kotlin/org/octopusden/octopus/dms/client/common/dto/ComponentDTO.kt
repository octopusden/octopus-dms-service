package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Component info",
    example = "{\n" +
            "  \"id\": \"ee-client-specific-component\",\n" +
            "  \"name\": \"EE Client Specific Component\",\n" +
            "  \"solution\": false,\n" +
            "  \"explicit\": true,\n" +
            "  \"clientCode\": \"CLIENT_CODE\",\n" +
            "  \"parentComponent\": \"ee-component\",\n" +
            "  \"securityGroups\": {\n" +
            "    \"read\": [\n" +
            "      \"Production Security\"\n" +
            "    ]\n" +
            "  },\n" +
            "  \"labels\": [\"production\", \"critical\"]\n" +
            "}"
)
data class ComponentDTO(
    val id: String,
    val name: String,
    val solution: Boolean,
    val explicit: Boolean,
    val clientCode: String?,
    val parentComponent: String?,
    val securityGroups: SecurityGroupsDTO,
    val labels: Set<String>
)
