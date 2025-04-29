package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO

@Deprecated("For backward compatibility only")
data class DeleteComponentVersionArtifactEvent(
    val component: String,
    val version: String,
    val artifact: ArtifactFullDTO
) : Event(EventType.DELETE_COMPONENT_VERSION_ARTIFACT)
