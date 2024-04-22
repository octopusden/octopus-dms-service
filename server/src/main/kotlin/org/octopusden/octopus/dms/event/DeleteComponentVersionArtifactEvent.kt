package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO

data class DeleteComponentVersionArtifactEvent(
    val component: String,
    val version: String,
    val artifact: ArtifactShortDTO
) : Event(EventType.DELETE_COMPONENT_VERSION_ARTIFACT)
