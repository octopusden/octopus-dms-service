package org.octopusden.octopus.dms.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "releng")
data class RelengProperties(
    val host: String,
    val apiUri: String = "rest/release-engineering/3"
)
