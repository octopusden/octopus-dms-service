package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactFullDTO
import org.octopusden.octopus.dms.entity.ComponentVersionArtifact
import org.octopusden.octopus.dms.entity.DockerArtifact
import org.octopusden.octopus.dms.entity.MavenArtifact
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ComponentVersionArtifactMapper(
    @Value("\${dms-service.docker-registry}") private val dockerRegistry: String
) {

    /**
     * Maps [ComponentVersionArtifact] to [ArtifactShortDTO]
     */
    fun mapToShortDTO(componentVersionArtifact: ComponentVersionArtifact): ArtifactShortDTO {
        return when (componentVersionArtifact.artifact.repositoryType) {
            RepositoryType.DOCKER -> {
                val artifact = componentVersionArtifact.artifact as DockerArtifact
                ArtifactShortDTO(
                    componentVersionArtifact.artifact.id,
                    componentVersionArtifact.artifact.repositoryType,
                    componentVersionArtifact.type,
                    componentVersionArtifact.displayName,
                    "$dockerRegistry/${artifact.image}:${artifact.tag}"
                )
            }

            else -> ArtifactShortDTO(
                componentVersionArtifact.artifact.id,
                componentVersionArtifact.artifact.repositoryType,
                componentVersionArtifact.type,
                componentVersionArtifact.displayName,
                componentVersionArtifact.artifact.fileName
            )
        }
    }


    /**
     * Maps list of [ComponentVersionArtifact] to list of [ArtifactFullDTO]
     */
    fun mapToFullDTO(componentVersionArtifact: ComponentVersionArtifact): ArtifactFullDTO {
        return when (componentVersionArtifact.artifact.repositoryType) {
            RepositoryType.MAVEN -> {
                componentVersionArtifact.artifact as MavenArtifact
                MavenArtifactFullDTO(
                    componentVersionArtifact.artifact.id,
                    componentVersionArtifact.type,
                    componentVersionArtifact.displayName,
                    componentVersionArtifact.artifact.fileName,
                    componentVersionArtifact.artifact.gav
                )
            }

            RepositoryType.DEBIAN -> DebianArtifactFullDTO(
                componentVersionArtifact.artifact.id,
                componentVersionArtifact.type,
                componentVersionArtifact.displayName,
                componentVersionArtifact.artifact.fileName,
                componentVersionArtifact.artifact.path
            )

            RepositoryType.RPM -> RpmArtifactFullDTO(
                componentVersionArtifact.artifact.id,
                componentVersionArtifact.type,
                componentVersionArtifact.displayName,
                componentVersionArtifact.artifact.fileName,
                componentVersionArtifact.artifact.path
            )

            RepositoryType.DOCKER -> {
                componentVersionArtifact.artifact as DockerArtifact
                DockerArtifactFullDTO(
                    componentVersionArtifact.artifact.id,
                    componentVersionArtifact.type,
                    componentVersionArtifact.displayName,
                    "$dockerRegistry/${componentVersionArtifact.artifact.image}:${componentVersionArtifact.artifact.tag}",
                    componentVersionArtifact.artifact.image,
                    componentVersionArtifact.artifact.tag
                )
            }
        }
    }

}