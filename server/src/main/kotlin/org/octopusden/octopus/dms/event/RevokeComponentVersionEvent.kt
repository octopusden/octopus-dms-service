package org.octopusden.octopus.dms.event

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionFullDTO

data class RevokeComponentVersionEvent(
    val componentVersion: ComponentVersionFullDTO,
    val artifacts: Set<ArtifactFullDTO>
) : Event(EventType.REVOKE_COMPONENT_VERSION)