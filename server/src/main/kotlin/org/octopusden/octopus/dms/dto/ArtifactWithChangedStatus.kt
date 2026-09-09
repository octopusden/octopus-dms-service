package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO

data class ArtifactWithChangedStatus(
    val artifact: ArtifactDTO,
    val changed: Boolean,
)
