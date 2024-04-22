package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO

data class DeleteComponentVersionArtifactsEvent(
    val component: String,
    val version: String,
    val artifacts: List<ArtifactShortDTO>
) : Event(EventType.DELETE_COMPONENT_VERSION_ARTIFACTS)
