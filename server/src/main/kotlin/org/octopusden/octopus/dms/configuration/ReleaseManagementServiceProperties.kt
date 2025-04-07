package org.octopusden.octopus.dms.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "release-management-service")
@ConstructorBinding
data class ReleaseManagementServiceProperties(
    val url: String,
    val retry: Int = 30000
)
