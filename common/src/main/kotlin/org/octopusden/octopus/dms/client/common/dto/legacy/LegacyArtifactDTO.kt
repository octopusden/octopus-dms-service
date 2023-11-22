package org.octopusden.octopus.dms.client.common.dto.legacy

import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.RepositoryType

class LegacyArtifactDTO(
    val id: Long,
    val repository: RepositoryType,
    val name: String,
    val type: ArtifactType,
    val displayName: String,
    val fileName: String,
    val version: String,
    val packaging: String,
    val classifier: String?,
    val storage: String
)
