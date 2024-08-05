package org.octopusden.octopus.dms.configuration.db.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.octopusden.octopus.components.registry.core.exceptions.NotFoundException
import org.octopusden.octopus.dms.configuration.RelengProperties
import org.octopusden.octopus.dms.service.ComponentsRegistryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class V20240802154520__component_version_fill_release_version(
    private val componentsRegistryService: ComponentsRegistryService,
) : BaseJavaMigration() {

    override fun migrate(context: Context?) {
        context!!.connection.createStatement().use { select ->
            select.executeQuery("SELECT cv.id, c.name, cv.version FROM component_version CV JOIN component C ON CV.component_id = C.id WHERE cv.release_version IS NULL ")
                .use { rows ->
                    while (rows.next()) {
                        val id = rows.getLong(1)
                        val component = rows.getString(2)
                        val version = rows.getString(3)
                        try {
                            val releaseVersion = componentsRegistryService.getDetailedComponentVersion(
                                component,
                                version
                            ).releaseVersion.version
                            context.connection.createStatement().use { update ->
                                update.execute("UPDATE component_version SET release_version='$releaseVersion' WHERE id=$id")
                            }
                        } catch (e: NotFoundException) {
                            log.error("No build found: '$component:$version'")
                        }
                    }
                }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(V20240802154520__component_version_fill_release_version::class.java)
    }
}
