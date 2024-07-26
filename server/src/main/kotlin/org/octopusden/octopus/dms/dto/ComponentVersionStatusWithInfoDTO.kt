package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatusDTO
import org.octopusden.releng.versions.IVersionInfo

class ComponentVersionStatusWithInfoDTO (
    component: String,
    version: String,
    status: BuildStatus,
    val versionInfo: IVersionInfo //cannot be deserialized
): ComponentVersionStatusDTO(component, version, status)