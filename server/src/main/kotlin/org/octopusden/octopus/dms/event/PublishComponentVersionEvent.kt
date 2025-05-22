package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionFullDTO

data class PublishComponentVersionEvent(
    val componentVersion: ComponentVersionFullDTO,
    val artifacts: Set<ArtifactFullDTO>
) : Event(EventType.PUBLISH_COMPONENT_VERSION)