package org.octopusden.octopus.dms.event

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(PublishComponentVersionEvent::class, name = "PUBLISH_COMPONENT_VERSION"),
    JsonSubTypes.Type(RevokeComponentVersionEvent::class, name = "REVOKE_COMPONENT_VERSION"),
    JsonSubTypes.Type(RegisterComponentVersionArtifactEvent::class, name = "REGISTER_COMPONENT_VERSION_ARTIFACT"),
    JsonSubTypes.Type(DeleteComponentVersionArtifactEvent::class, name = "DELETE_COMPONENT_VERSION_ARTIFACT")
)
abstract class Event(
    val type: EventType
)