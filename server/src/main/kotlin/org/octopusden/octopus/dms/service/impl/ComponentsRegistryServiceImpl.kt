package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider
import org.octopusden.octopus.components.registry.core.dto.Component
import org.octopusden.octopus.components.registry.core.dto.DetailedComponentVersion
import org.octopusden.octopus.components.registry.core.dto.VersionRequest
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.SecurityGroupsDTO
import org.octopusden.octopus.dms.configuration.ComponentsRegistryServiceProperties
import org.octopusden.octopus.dms.exception.IllegalComponentTypeException
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.releng.versions.NumericVersionFactory
import org.octopusden.releng.versions.ReversedVersionComparator
import org.octopusden.releng.versions.VersionNames
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class ComponentsRegistryServiceImpl(
    private val componentsRegistryServiceProperties: ComponentsRegistryServiceProperties
) : ComponentsRegistryService {
    private val client = ClassicComponentsRegistryServiceClient(
        object : ClassicComponentsRegistryServiceClientUrlProvider {
            override fun getApiUrl() = componentsRegistryServiceProperties.url
        }
    )

    override fun isComponentExists(component: String) = try {
        client.getById(component).id == component
    } catch (_: NotFoundException) {
        false
    }

    override fun getExternalComponent(component: String) = client.getById(component).let {
        if (it.distribution?.external != true) {
            throw IllegalComponentTypeException("Component '$component' is not external")
        }
        it.toComponentDTO()
    }

    override fun getExternalComponents(filter: ComponentRequestFilter?) =
        client.getAllComponents(solution = filter?.solution).components
            .filter { component ->
                !component.archived && (component.distribution?.let { d -> d.external && (filter?.explicit == false || d.explicit)} ?: false)
            }.map {
                it.toComponentDTO()
            }

    override fun getDetailedComponentVersion(component: String, version: String) =
        client.getDetailedComponentVersion(component, version)

    override fun getVersionNames() = with(client.getVersionNames()) {
        VersionNames(this.serviceBranch, this.service, this.minor)
    }

    override fun findPreviousVersion(component: String, version: String, versions: List<String>): String {
        val versionNames = getVersionNames()
        val currentVersion = client.getDetailedComponentVersion(component, version)
        return client.getDetailedComponentVersions(component, VersionRequest(versions))
            .versions
            .toSortedMap(ReversedVersionComparator(versionNames))
            .entries
            .firstOrNull { matches(component, versionNames, currentVersion, it.value) }
            ?.key
            ?: ""
    }

    override fun findPreviousLines(component: String, version: String, versions: List<String>): List<String> {
        val versionNames = getVersionNames()
        val numericVersionFactory = NumericVersionFactory(versionNames)
        val numericVersion = numericVersionFactory.create(version)
        return client.getDetailedComponentVersions(component, VersionRequest(versions))
            .versions
            .map { it.value.lineVersion.version to it.key }
            .groupBy({ (lineVersion, _) -> lineVersion }, { (_, sameLineVersion) -> sameLineVersion })
            .mapNotNull { (_, sameLineVersions) ->
                sameLineVersions.sortedWith(ReversedVersionComparator(versionNames)).firstOrNull()
            }
            .filter { v -> numericVersionFactory.create(v) < numericVersion }
            .sortedWith { a, b -> ReversedVersionComparator(versionNames).compare(a, b) }
            .reversed()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ComponentsRegistryServiceImpl::class.java)

        private fun Component.toComponentDTO() = ComponentDTO(
            id,
            name ?: id,
            solution == true,
            distribution?.explicit == true,
            clientCode,
            parentComponent,
            SecurityGroupsDTO(distribution?.securityGroups?.read ?: emptyList()),
            labels
        )

        private fun matches(
            component: String,
            versionNames: VersionNames,
            currentVersion: DetailedComponentVersion,
            versionToBeCheck: DetailedComponentVersion
        ): Boolean {
            val minorOfCurrentVersion = currentVersion.lineVersion
            val minorOfVersionToBeCheck = versionToBeCheck.lineVersion
            return (minorOfCurrentVersion == minorOfVersionToBeCheck
                    && ReversedVersionComparator(versionNames).compare(
                currentVersion.releaseVersion.version,
                versionToBeCheck.releaseVersion.version
            ) < 0
                    )
                .also {
                    log.debug(
                        "Comparing {} versions {}(minor={}) and {}(minor={}) with result {}",
                        component,
                        currentVersion.releaseVersion.version,
                        minorOfCurrentVersion,
                        versionToBeCheck.releaseVersion.version,
                        minorOfVersionToBeCheck.version,
                        it
                    )
                }
        }
    }
}
