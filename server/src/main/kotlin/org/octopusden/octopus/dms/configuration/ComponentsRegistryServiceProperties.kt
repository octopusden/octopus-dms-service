package org.octopusden.octopus.dms.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "components-registry-service")
@ConstructorBinding
data class ComponentsRegistryServiceProperties(
    val url: String
)
