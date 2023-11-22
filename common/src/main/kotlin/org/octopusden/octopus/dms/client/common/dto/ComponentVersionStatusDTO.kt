package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
        description = "Component version status",
        example = "{\n" +
                "  \"version\": \"1.2.3\",\n" +
                "  \"status\": \"RELEASE\"\n" +
                "}"
)
open class ComponentVersionStatusDTO(
        val version: String,
        val status: BuildStatus
) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is ComponentVersionStatusDTO) return false
                if (version != other.version) return false
                if (status != other.status) return false
                return true
        }

        override fun hashCode(): Int {
                var result = version.hashCode()
                result = 31 * result + status.hashCode()
                return result
        }
}
