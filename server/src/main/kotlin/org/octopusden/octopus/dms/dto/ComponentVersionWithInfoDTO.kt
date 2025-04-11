package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.releng.versions.IVersionInfo

data class ComponentVersionWithInfoDTO(val version: ComponentVersionDTO, val versionInfo: IVersionInfo)
