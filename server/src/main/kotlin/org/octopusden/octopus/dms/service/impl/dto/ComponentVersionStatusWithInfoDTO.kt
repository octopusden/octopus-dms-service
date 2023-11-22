package org.octopusden.octopus.dms.service.impl.dto

import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatusDTO
import org.octopusden.releng.versions.IVersionInfo

class ComponentVersionStatusWithInfoDTO (
    version: String,
    status: BuildStatus,
    val versionInfo: IVersionInfo //cannot be deserialized
): ComponentVersionStatusDTO(version, status)