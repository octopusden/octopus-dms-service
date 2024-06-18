package org.octopusden.octopus.dms.controller.ui.dto

import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatusDTO

class GroupedComponentVersionsDTO(
    val groupName: String, val versions: List<ComponentVersionStatusDTO>, val children: List<GroupedComponentVersionsDTO>,
)
