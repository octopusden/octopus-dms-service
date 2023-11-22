package org.octopusden.octopus.dms.client.common.dto

import com.fasterxml.jackson.annotation.JsonValue

enum class ArtifactType(val type: String) {
    NOTES("notes"),
    DISTRIBUTION("distribution"),
    REPORT("report"),
    STATIC("static"),
    MANUALS("documentation");

    @JsonValue
    fun value(): String {
        return type
    }

    companion object {
        @JvmStatic
        fun findByType(type: String) = values().firstOrNull { it.type == type }
    }

    override fun toString(): String {
        return type
    }
}
