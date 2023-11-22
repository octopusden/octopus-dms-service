package org.octopusden.octopus.dms.client.common.dto.legacy

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "URL DTO")
data class ComponentUrlDTO(@Schema(example = "https://artifactory.corp.domain/repository/maven-releases") val url: String) {
    @JsonProperty("URL")
    fun getURL() = url
}
