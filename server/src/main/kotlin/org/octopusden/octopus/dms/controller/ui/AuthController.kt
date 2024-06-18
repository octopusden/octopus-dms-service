package org.octopusden.octopus.dms.controller.ui

import org.octopusden.cloud.commons.security.SecurityService
import org.octopusden.cloud.commons.security.dto.User
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("ui/auth")
@RestController
class AuthController(private val securityService: SecurityService) {
    @GetMapping("me")
    fun getUserInfo(): User {
        val user = securityService.getCurrentUser()
        if (log.isTraceEnabled) {
            log.trace("Logged User: ${user.username}")
        }
        return user
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthController::class.java)
    }
}
