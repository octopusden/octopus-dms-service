package org.octopusden.octopus.dms.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "release-management-service")
data class ReleaseManagementServiceProperties(
    val url: String,
    val retry: Int = 30000
)
