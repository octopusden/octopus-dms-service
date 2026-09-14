package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO

data class ArtifactWriteResult(
    val artifact: ArtifactDTO,
    val changed: Boolean,
)
