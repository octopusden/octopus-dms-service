package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Date

@Schema(
    description = "Full component version info",
    example = "{\n" +
            "  \"component\": \"Some_Component\",\n" +
            "  \"version\": \"1.2.3\",\n" +
            "  \"published\": true,\n" +
            "  \"status\": \"RELEASE\",\n" +
            "  \"promotedAt\": \"2022-01-01T12:00:00.000+00:00\"\n" +
            "}"
)
class ComponentVersionFullDTO(
    component: String,
    version: String,
    published: Boolean,
    status: ComponentVersionStatus,
    val promotedAt: Date?
) : ComponentVersionDTO(component, version, published, status) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComponentVersionFullDTO) return false
        if (!super.equals(other)) return false
        if (promotedAt != other.promotedAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (promotedAt?.hashCode() ?: 0)
        return result
    }

    override fun toString() =
        "ComponentVersionFullDTO(component='$component', version='$version', published=$published, status=$status, promotedAt=$promotedAt)"
}