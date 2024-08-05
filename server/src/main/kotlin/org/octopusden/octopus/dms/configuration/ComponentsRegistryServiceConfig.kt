package org.octopusden.octopus.dms.configuration

import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ComponentsRegistryServiceConfig(private val componentsRegistryServiceProperties: ComponentsRegistryServiceProperties) {

    @Bean
    fun componentsRegistryServiceClient() = ClassicComponentsRegistryServiceClient(
        object : ClassicComponentsRegistryServiceClientUrlProvider {
            override fun getApiUrl() = componentsRegistryServiceProperties.url
        }
    )
}