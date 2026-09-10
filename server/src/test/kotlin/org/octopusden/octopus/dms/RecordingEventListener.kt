package org.octopusden.octopus.dms

import org.octopusden.octopus.dms.event.Event
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.concurrent.CopyOnWriteArrayList

@TestConfiguration
class RecordingEventListener {
    private val recorded = CopyOnWriteArrayList<Event>()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onEvent(event: Event) {
        recorded.add(event)
    }

    fun clear() {
        recorded.clear()
    }

    fun events(): List<Event> = recorded.toList()
}
