package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "Component version info",
    example = "{\n" +
            "  \"component\": \"Some_Component\",\n" +
            "  \"version\": \"1.2.3\",\n" +
            "  \"published\": true,\n" +
            "  \"status\": \"RELEASE\"\n" +
            "}"
)
open class ComponentVersionDTO(
    val component: String,
    val version: String,
    val published: Boolean,
    val status: ComponentVersionStatus
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComponentVersionDTO

        if (component != other.component) return false
        if (version != other.version) return false
        if (published != other.published) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode() = Objects.hash(component, version, published, status)

    override fun toString() = "ComponentVersionDTO(component='$component', version='$version', published=$published, status=$status)"
}