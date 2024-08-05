package org.octopusden.octopus.dms.configuration.db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class V20240802154520__component_version_fill_release_version(
    private val componentsRegistryServiceClient: ComponentsRegistryServiceClient
) : BaseJavaMigration() {

    override fun migrate(context: Context?) {
        val errors = mutableListOf<String>()
        context!!.connection.createStatement().use { select ->
            select.executeQuery("SELECT cv.id, c.name, cv.version FROM component_version CV JOIN component C ON CV.component_id = C.id WHERE cv.release_version IS NULL ")
                .use { rows ->
                    while (rows.next()) {
                        val id = rows.getLong(1)
                        val component = rows.getString(2)
                        val version = rows.getString(3)
                        try {
                            val releaseVersion = componentsRegistryServiceClient.getDetailedComponentVersion(
                                component,
                                version
                            ).releaseVersion.version
                            context.connection.createStatement().use { update ->
                                update.execute("UPDATE component_version SET release_version='$releaseVersion' WHERE id=$id")
                            }
                        } catch (e: NotFoundException) {
                            try {
                                componentsRegistryServiceClient.getById(component).distribution
                                    ?.let { distribution ->
                                        if (distribution.explicit && distribution.external) {
                                            log.error("Version range not found for $component:$version")
                                            errors.add("$component:$version")
                                        } else {
                                            null
                                        }
                                    } ?: kotlin.run {
                                    log.warn("Version range not found and component is not EE: '$component:$version'")
                                    context.connection.createStatement().use { delete ->
//                                        delete.execute("DELETE FROM component_version WHERE id=$id")
                                    }
                                }
                                log.error("'$component:$version' not found in Components Registry")
                            } catch (e: NotFoundException) {
                                log.warn("'$component' not found in Components Registry, deleting ComponentVersion '$component:$version'")
                            }
                        }
                    }
                }
        }
        if (errors.isNotEmpty()) {
            throw IllegalStateException("There is migration errors, because Version Ranges not found for: ${errors.joinToString()}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(V20240802154520__component_version_fill_release_version::class.java)
    }
}
