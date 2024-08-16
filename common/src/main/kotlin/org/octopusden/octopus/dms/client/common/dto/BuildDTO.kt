package org.octopusden.octopus.dms.client.common.dto

import java.util.Date

class BuildDTO(component: String, version: String, status: BuildStatus, val promotedAt: Date?) :
    ComponentVersionStatusDTO(component, version, status)
