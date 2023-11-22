package org.octopusden.octopus.dms.service.impl

import org.octopusden.octopus.dms.client.common.dto.ComponentDTO
import org.octopusden.octopus.dms.client.common.dto.SecurityGroupsDTO
import org.octopusden.octopus.dms.configuration.ComponentsRegistryServiceProperties
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider
import org.octopusden.octopus.components.registry.core.dto.DetailedComponentVersion
import org.octopusden.octopus.components.registry.core.dto.VersionRequest
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

    override fun getComponents() = client.getAllComponents().components
        .filter { it.distribution?.let { d -> d.explicit && d.external } ?: false }
        .map {
            ComponentDTO(
                it.id, // Component name
                it.name ?: it.id, // Component display name
                SecurityGroupsDTO(it.distribution?.securityGroups?.read ?: emptyList())
            )
        }

    override fun getComponentReadSecurityGroups(component: String) =
        client.getById(component).distribution?.securityGroups?.read ?: emptyList()

    override fun checkComponent(component: String) {
        val distribution = client.getById(component).distribution
        if (distribution == null || !distribution.explicit || !distribution.external) {
            throw NotFoundException("'$component' is not explicit and external component")
        }
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
                log.debug("Comparing $component versions ${currentVersion.releaseVersion.version}(minor=$minorOfCurrentVersion) and ${versionToBeCheck.releaseVersion.version}(minor=${minorOfVersionToBeCheck.version}) with result $it")
            }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ComponentsRegistryServiceImpl::class.java)
    }
}
