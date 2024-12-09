package org.octopusden.octopus.dms.webhook

import org.octopusden.octopus.dms.event.Event
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

@ConditionalOnProperty(prefix = "dms-service.webhook", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@Component
class WebhookEventListener(
    val webhookFeignClient: WebhookFeignClient
) {
    @Async
    @TransactionalEventListener
    fun onEvent(event: Event) {
        log.info("Sending ${event.type} webhook")
        try {
            webhookFeignClient.post(event)
        } catch (e: Exception) {
            with("Exception when sending ${event.type} webhook") {
                log.warn("$this: ${e.message}")
                log.debug(this, e)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WebhookEventListener::class.java)
    }
}