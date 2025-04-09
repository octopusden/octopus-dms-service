package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO

data class RevokeComponentVersionEvent(
    val component: String,
    val version: String,
    val artifacts: List<ArtifactShortDTO>
) : Event(EventType.REVOKE_COMPONENT_VERSION)