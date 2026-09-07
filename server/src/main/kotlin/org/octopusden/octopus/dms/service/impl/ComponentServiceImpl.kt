package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.*
import org.octopusden.octopus.dms.dto.BuildDTO
import org.octopusden.octopus.dms.dto.BuildFullDTO
import org.octopusden.octopus.dms.dto.ComponentVersionWithInfoDTO
import org.octopusden.octopus.dms.dto.DependencyArtifactsDTO
import org.octopusden.octopus.dms.entity.ComponentVersion
import org.octopusden.octopus.dms.event.PublishComponentVersionEvent
import org.octopusden.octopus.dms.event.RevokeComponentVersionEvent
import org.octopusden.octopus.dms.exception.IllegalComponentTypeException
import org.octopusden.octopus.dms.exception.VersionPublishedException
import org.octopusden.octopus.dms.repository.ComponentRepository
import org.octopusden.octopus.dms.repository.ComponentVersionArtifactRepository
import org.octopusden.octopus.dms.repository.ComponentVersionRepository
import org.octopusden.octopus.dms.repository.getByComponentNameAndVersion
import org.octopusden.octopus.dms.service.ComponentService
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.dms.service.ReleaseManagementService
import org.octopusden.releng.versions.NumericVersionFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ComponentServiceImpl(
    private val componentsRegistryService: ComponentsRegistryService,
    private val releaseManagementService: ReleaseManagementService,
    private val componentRepository: ComponentRepository,
    private val componentVersionRepository: ComponentVersionRepository,
    private val componentVersionArtifactRepository: ComponentVersionArtifactRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @param:Value("\${dms-service.docker-registry}") private val dockerRegistry: String,
) : ComponentService {
    override fun getComponents(filter: ComponentRequestFilter?): List<ComponentDTO> {
        return componentsRegistryService.getExternalComponents(filter).sortedWith { a, b ->
            a.name.lowercase().compareTo(b.name.lowercase())
        }
    }

    @Transactional(readOnly = true)
    override fun getComponentMinorVersions(componentName: String): Set<String> {
        componentsRegistryService.getExternalExplicitComponent(componentName)
        return componentVersionRepository.getMinorVersionsByComponentName(componentName)
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionsWithInfo(
        componentName: String,
        minorVersions: List<String>,
        includeRc: Boolean,
    ): List<ComponentVersionWithInfoDTO> {
        val componentVersions = getComponentVersions(componentName, minorVersions, includeRc)
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        return componentVersions.map { ComponentVersionWithInfoDTO(it, numericVersionFactory.create(it.version)) }
    }

    @Transactional(readOnly = true)
    override fun getComponentVersionDependencies(
        componentName: String,
        version: String,
    ): List<ComponentVersionWithInfoDTO> {
        if (!componentsRegistryService.getExternalExplicitComponentVersion(componentName, version).solution) {
            throw IllegalComponentTypeException("Component '$componentName' is not solution")
        }
        val release = releaseManagementService.getRelease(componentName, version, true)
        val numericVersionFactory = NumericVersionFactory(componentsRegistryService.getVersionNames())
        return release.dependencies.filter { it.status != ComponentVersionStatus.BUILD }.mapNotNull { dependency ->
            componentVersionRepository
                .findByComponentNameAndVersion(
                    dependency.component,
                    dependency.version,
                )?.let {
                    ComponentVersionWithInfoDTO(it.toDTO(dependency), numericVersionFactory.create(it.version))
                }
        }
    }

    @Transactional(readOnly = false)
    override fun patchComponentVersion(
        componentName: String,
        version: String,
        patchComponentVersionDTO: PatchComponentVersionDTO,
    ): ComponentVersionDTO {
        val component = componentsRegistryService.getExternalExplicitComponentVersion(componentName, version)
        val release = releaseManagementService.getRelease(component.id, version, !patchComponentVersionDTO.published)
        componentRepository.lock(component.id.hashCode())
        val componentVersion = componentVersionRepository.getByComponentNameAndVersion(component.id, release.version)
        return if (componentVersion.published == patchComponentVersionDTO.published) {
            componentVersion.toDTO(release)
        } else {
            val artifacts = componentVersionArtifactRepository.findByComponentVersion(componentVersion).map {
                it.toFullDTO(dockerRegistry)
            }
            val dependencies = mutableListOf<DependencyArtifactsDTO>()
            if (component.solution) {
                // TODO: for now, EE dependencies artifacts are included in both publish and revoke events for solution components
                //      behaviour could be changed later - it may be required not to add dependencies artifacts in revoke event
                val unpublishedDependencies = mutableListOf<BuildDTO>()
                // TODO: add a cache for component information.
                release.dependencies.forEach { dependencyBuild ->
                    if (componentsRegistryService
                            .getExternalComponentVersion(
                                dependencyBuild.component,
                                dependencyBuild.version,
                            ).explicit
                    ) {
                        val dependencyComponentVersion = componentVersionRepository.findByComponentNameAndVersion(
                            dependencyBuild.component,
                            dependencyBuild.version,
                        )
                        if (dependencyComponentVersion?.published != true) {
                            unpublishedDependencies.add(dependencyBuild)
                        } else {
                            dependencies.add(
                                DependencyArtifactsDTO(
                                    dependencyComponentVersion.toDTO(dependencyBuild),
                                    componentVersionArtifactRepository
                                        .findByComponentVersion(
                                            dependencyComponentVersion,
                                        ).map { it.toFullDTO(dockerRegistry) },
                                ),
                            )
                        }
                    }
                }
                if (patchComponentVersionDTO.published && unpublishedDependencies.isNotEmpty()) {
                    throw VersionPublishedException(
                        "Unable to publish version '${release.version}' of solution '${component.id}'. It has unpublished dependencies $unpublishedDependencies",
                    )
                }
            } else if (!patchComponentVersionDTO.published) {
                release.parents
                    .filter {
                        componentsRegistryService.getExternalComponent(it.component).solution &&
                            componentVersionRepository
                                .findByComponentNameAndVersion(
                                    it.component,
                                    it.version,
                                )?.published == true
                    }.takeIf { it.isNotEmpty() }
                    ?.let {
                        throw VersionPublishedException(
                            "Unable to revoke version '${release.version}' of component '${component.id}'. It is dependency of published solutions $it",
                        )
                    }
            }
            applicationEventPublisher.publishEvent(
                if (patchComponentVersionDTO.published) {
                    PublishComponentVersionEvent(
                        componentVersion.toFullDTO(component, release),
                        artifacts,
                        dependencies,
                    )
                } else {
                    RevokeComponentVersionEvent(
                        componentVersion.toFullDTO(component, release),
                        artifacts,
                        dependencies,
                    )
                },
            )
            componentVersionRepository
                .save(
                    componentVersion.apply {
                        published = patchComponentVersionDTO.published
                    },
                ).toDTO(release)
        }
    }

    @Transactional(readOnly = true)
    override fun getPreviousLinesLatestVersions(
        componentName: String,
        version: String,
        includeRc: Boolean,
    ): List<String> {
        val componentVersions = getComponentVersions(componentName, emptyList(), includeRc)
        return componentsRegistryService.findPreviousLines(
            componentName,
            releaseManagementService.getRelease(componentName, version, true).version,
            componentVersions.map { it.version },
        )
    }

    private fun getComponentVersions(
        componentName: String,
        minorVersions: List<String>,
        includeRc: Boolean,
    ): List<ComponentVersionDTO> {
        componentsRegistryService.getExternalExplicitComponent(componentName)
        val componentVersions = if (minorVersions.isEmpty()) {
            componentVersionRepository.findByComponentName(componentName)
        } else {
            componentVersionRepository.findByComponentNameAndMinorVersionIn(componentName, minorVersions)
        }
        return if (componentVersions.isEmpty()) {
            emptyList()
        } else {
            val releases = releaseManagementService
                .findReleases(
                    componentName,
                    componentVersions.map { it.version },
                    includeRc,
                ).associateBy { it.version }
            componentVersions.mapNotNull {
                releases[it.version]?.let { release -> it.toDTO(release) }
            }
        }
    }

    private fun ComponentVersion.toDTO(build: BuildDTO) =
        ComponentVersionDTO(
            component.name,
            version,
            published,
            build.status,
            build.hotfix,
        )

    companion object {
        private val log = LoggerFactory.getLogger(ComponentServiceImpl::class.java)

        fun ComponentVersion.toFullDTO(
            component: ComponentDTO,
            build: BuildFullDTO,
        ) = ComponentVersionFullDTO(
            component.id,
            version,
            published,
            build.status,
            build.hotfix,
            build.promotedAt,
            component.name,
            component.solution,
            component.clientCode,
            component.parentComponent,
            component.labels,
            build.limitations,
        )
    }
}
