package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO

data class DependencyArtifactsDTO(
    val componentVersion: ComponentVersionDTO,
    val artifacts: List<ArtifactFullDTO>
)
