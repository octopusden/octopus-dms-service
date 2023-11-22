package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "MAVEN GAV",
    example = "{\n" +
            "  \"groupId\": \"domain.corp.distribution\",\n" +
            "  \"artifactId\": \"some-app\",\n" +
            "  \"version\": \"1.2.3\",\n" +
            "  \"packaging\": \"jar\",\n" +
            "  \"classifier\": null\n" +
            "}"
)
data class GavDTO(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val packaging: String,
    val classifier: String? = null
) {
    fun toPath() = groupId.replace('.', '/') + "/$artifactId/$version/$artifactId-$version" + (classifier?.let { "-$classifier." } ?: ".") + packaging

    override fun toString() = "$groupId:$artifactId:$version:$packaging" + (classifier?.let { c -> ":$c" } ?: "")
}
