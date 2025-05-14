package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "Full RPM artifact info",
    example = "{\n" +
            "  \"id\": 3,\n" +
            "  \"repositoryType\": \"RPM\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app.el8.x86_64.rpm\",\n" +
            "  \"fileName\": \"some-app-1.2.3-1.el8.x86_64.rpm\",\n" +
            "  \"sha256\": \"5097f3c7c69dc04e012fe9fe710219c6e60f28febf4c31b932ecf97b07118296\",\n"+
            "  \"rpm\": \"some-app/some-app-1.2.3-1.el8.x86_64.rpm\"\n" +
            "}"
)
class RpmArtifactFullDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    sha256: String,
    val rpm: String
) : ArtifactFullDTO(id, RepositoryType.RPM, type, displayName, fileName, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RpmArtifactFullDTO

        return rpm == other.rpm
    }

    override fun hashCode() = Objects.hash(id, type, displayName, fileName, sha256, rpm)

    override fun toString() = "RpmArtifactFullDTO(id=$id, type=$type, displayName='$displayName', fileName='$fileName', sha256='$sha256, rpm='$rpm')"
}
