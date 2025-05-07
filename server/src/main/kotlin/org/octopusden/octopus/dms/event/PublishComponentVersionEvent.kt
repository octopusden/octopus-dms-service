package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO

data class PublishComponentVersionEvent(
    val component: String,
    val version: String,
    val artifacts: List<ArtifactShortDTO>,
    val clientCode: String,
) : Event(EventType.PUBLISH_COMPONENT_VERSION)