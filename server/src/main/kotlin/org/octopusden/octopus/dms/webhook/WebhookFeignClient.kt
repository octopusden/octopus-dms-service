package org.octopusden.octopus.dms.webhook

import org.octopusden.octopus.dms.event.Event
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping

@ConditionalOnProperty(prefix = "dms-service.webhook", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@FeignClient("webhook", url = "\${dms-service.webhook.url}")
interface WebhookFeignClient {
    @PostMapping
    fun post(event: Event)
}