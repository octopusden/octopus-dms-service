package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO

data class RegisterComponentVersionArtifactEvent(
    val component: String,
    val version: String,
    val artifact: ArtifactFullDTO
) : Event(EventType.REGISTER_COMPONENT_VERSION_ARTIFACT)