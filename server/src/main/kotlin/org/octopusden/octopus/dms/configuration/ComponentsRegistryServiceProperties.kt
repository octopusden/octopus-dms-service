package org.octopusden.octopus.dms.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "components-registry-service")
data class ComponentsRegistryServiceProperties(
    val url: String
)
