package org.octopusden.octopus.dms.configuration

import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.cloud.commons.security.config.CloudCommonWebSecurityConfig
import org.octopusden.cloud.commons.security.config.SecurityProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(AuthServerClient::class)
class WebSecurityConfig(authServerClient: AuthServerClient) : CloudCommonWebSecurityConfig(
    authServerClient = authServerClient,
    securityProperties = SecurityProperties(emptyMap())
)