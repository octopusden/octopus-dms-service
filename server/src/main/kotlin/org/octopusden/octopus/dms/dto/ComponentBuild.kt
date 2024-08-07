package org.octopusden.octopus.dms.dto

import org.octopusden.octopus.dms.client.common.dto.BuildStatus

data class ComponentBuild(val status: BuildStatus, val version: String)