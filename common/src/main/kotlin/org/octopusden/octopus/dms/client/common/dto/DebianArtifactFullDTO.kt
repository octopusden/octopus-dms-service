package org.octopusden.octopus.dms.client.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.Objects

@Schema(
    description = "Full DEBIAN artifact info",
    example = "{\n" +
            "  \"id\": 2,\n" +
            "  \"repositoryType\": \"DEBIAN\",\n" +
            "  \"type\": \"distribution\",\n" +
            "  \"displayName\": \"some-app_amd64.deb\",\n" +
            "  \"fileName\": \"some-app_0.1.2-3_amd64.deb\",\n" +
            "  \"sha256\": \"8247830a7fae3307ac34d6d9b8426d31568f7774bc0085bbe5ba1e4534ba8337\",\n"+
            "  \"deb\": \"pool/s/some-app/some-app_0.1.2-3_amd64.deb\"\n" +
            "}"
)
class DebianArtifactFullDTO(
    id: Long,
    type: ArtifactType,
    displayName: String,
    fileName: String,
    sha256: String,
    val deb: String
) : ArtifactFullDTO(id, RepositoryType.DEBIAN, type, displayName, fileName, sha256) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as DebianArtifactFullDTO

        return deb == other.deb
    }

    override fun hashCode() = Objects.hash(id, type, displayName, fileName, sha256, deb)

    override fun toString() = "DebianArtifactFullDTO(id=$id, type=$type, displayName='$displayName', fileName='$fileName', sha256='$sha256', deb='$deb')"
}