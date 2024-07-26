package org.octopusden.octopus.dms.client.common.dto

data class DependencyDTO(val component: ComponentDTO, val version: String, val status: BuildStatus)
