package org.octopusden.octopus.dms.configuration

import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.cloud.commons.security.config.CloudCommonWebSecurityConfig
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.WebSecurity

@Configuration
@Import(AuthServerClient::class)
class WebSecurityConfig(authServerClient: AuthServerClient) : CloudCommonWebSecurityConfig(authServerClient) {
    override fun configure(web: WebSecurity) {
        super.configure(web)
        web.ignoring().antMatchers("/v3/api-docs", "/v3/api-docs/swagger-config")
    }
}
