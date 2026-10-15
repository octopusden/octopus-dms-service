package org.octopusden.octopus.dms.event

data class ArchiveComponentEvent(
    val component: String,
) : Event(EventType.ARCHIVE_COMPONENT)
