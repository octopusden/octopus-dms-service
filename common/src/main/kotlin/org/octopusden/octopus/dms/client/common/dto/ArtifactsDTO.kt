package org.octopusden.octopus.dms.client.common.dto

data class ArtifactsDTO(
    val componentVersion: ComponentVersionFullDTO,
    val artifacts: List<ArtifactShortDTO>
)