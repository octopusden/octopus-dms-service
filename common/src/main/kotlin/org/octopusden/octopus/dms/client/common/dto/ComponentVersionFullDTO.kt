package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Date
import java.util.Objects

@Schema(
    description = "Full component version info",
    example = "{\n" +
            "  \"component\": \"ee-client-specific-component\",\n" +
            "  \"version\": \"1.2.3\",\n" +
            "  \"published\": true,\n" +
            "  \"status\": \"RELEASE\",\n" +
            "  \"promotedAt\": \"2022-01-01T12:00:00.000+00:00\",\n" +
            "  \"displayName\": \"EE Client Specific Component\",\n" +
            "  \"solution\": false,\n" +
            "  \"clientCode\": \"CLIENT_CODE\",\n" +
            "  \"parentComponent\": \"ee-component\"\n" +
            "}"
)
class ComponentVersionFullDTO(
    component: String,
    version: String,
    published: Boolean,
    status: ComponentVersionStatus,
    val promotedAt: Date?,
    val displayName: String,
    val solution: Boolean,
    val clientCode: String?,
    val parentComponent: String?
) : ComponentVersionDTO(component, version, published, status) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ComponentVersionFullDTO

        if (promotedAt != other.promotedAt) return false
        if (displayName != other.displayName) return false
        if (solution != other.solution) return false
        if (clientCode != other.clientCode) return false
        if (parentComponent != other.parentComponent) return false

        return true
    }

    override fun hashCode() = Objects.hash(super.hashCode(), promotedAt, displayName, solution, clientCode, parentComponent)
    override fun toString(): String {
        return "ComponentVersionFullDTO(component='$component', version='$version', published=$published, status=$status, promotedAt=$promotedAt, displayName='$displayName', solution=$solution, clientCode=$clientCode, parentComponent=$parentComponent)"
    }
}