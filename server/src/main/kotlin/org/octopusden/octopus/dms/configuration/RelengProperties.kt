package org.octopusden.octopus.dms.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "releng")
@ConstructorBinding
data class RelengProperties(
    val host: String,
    val apiUri: String = "rest/release-engineering/3"
)
