package org.octopusden.octopus.dms.configuration

import feign.auth.BasicAuthRequestInterceptor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean

class WebhookConfig(private val webhookProperties: WebhookProperties) {
    @ConfigurationProperties("dms-service.webhook")
    @ConditionalOnProperty(
        prefix = "dms-service.webhook", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    @ConstructorBinding
    data class WebhookProperties(
        val url: String,
        val user: String? = null,
        val password: String? = null
    )

    @Bean
    fun basicAuthRequestInterceptor() =
        if (webhookProperties.user.isNullOrBlank() || webhookProperties.password.isNullOrBlank()) null
        else BasicAuthRequestInterceptor(webhookProperties.user, webhookProperties.password)
}