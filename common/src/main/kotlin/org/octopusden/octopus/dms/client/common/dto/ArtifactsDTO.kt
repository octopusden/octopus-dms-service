package org.octopusden.octopus.dms.client.common.dto

data class ArtifactsDTO(val build: BuildDTO, val artifacts: List<ArtifactShortDTO>)