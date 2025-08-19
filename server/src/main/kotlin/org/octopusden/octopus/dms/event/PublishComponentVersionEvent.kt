package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionFullDTO
import org.octopusden.octopus.dms.dto.DependencyArtifactsDTO

data class PublishComponentVersionEvent(
    val componentVersion: ComponentVersionFullDTO,
    val artifacts: List<ArtifactFullDTO>,
    val dependencies: List<DependencyArtifactsDTO>
) : Event(EventType.PUBLISH_COMPONENT_VERSION)