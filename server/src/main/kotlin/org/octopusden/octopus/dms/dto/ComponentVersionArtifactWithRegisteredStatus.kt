package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO

data class ComponentVersionArtifactWithRegisteredStatus(
    val artifact: ArtifactFullDTO,
    val registered: Boolean,
)
