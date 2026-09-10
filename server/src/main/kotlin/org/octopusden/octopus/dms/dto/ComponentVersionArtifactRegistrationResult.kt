package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO

data class ComponentVersionArtifactRegistrationResult(
    val artifact: ArtifactFullDTO,
    val created: Boolean,
)
