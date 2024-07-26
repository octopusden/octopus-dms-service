package org.octopusden.octopus.dms.configuration

import org.octopusden.octopus.releasemanagementservice.client.ReleaseManagementServiceClient
import org.octopusden.octopus.releasemanagementservice.client.impl.ClassicReleaseManagementServiceClient
import org.octopusden.octopus.releasemanagementservice.client.impl.ReleaseManagementServiceClientParametersProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReleaseManagementServiceConfig(private val releaseManagementServiceProperties: ReleaseManagementServiceProperties) {

    @Bean
    fun releaseManagementServiceClient(): ReleaseManagementServiceClient =
        ClassicReleaseManagementServiceClient(object : ReleaseManagementServiceClientParametersProvider {
            override fun getApiUrl(): String = releaseManagementServiceProperties.url

            override fun getTimeRetryInMillis(): Int = 30000
        })

    @ConfigurationProperties(prefix = "release-management-service")
    @ConstructorBinding
    data class ReleaseManagementServiceProperties(val url: String)
}
