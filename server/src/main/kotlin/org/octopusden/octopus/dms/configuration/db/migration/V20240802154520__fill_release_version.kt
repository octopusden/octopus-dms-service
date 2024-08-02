package org.octopusden.octopus.dms.configuration.db.migration

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import khttp.get
import khttp.responses.Response
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.octopusden.octopus.dms.client.common.dto.BuildStatus
import org.octopusden.octopus.dms.configuration.RelengProperties
import org.octopusden.octopus.dms.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class V20240802154520__component_version_fill_release_version(
    private val objectMapper: ObjectMapper,
    relengProperties: RelengProperties,
) : BaseJavaMigration() {

    private val relengUrl = "${relengProperties.host}/${relengProperties.apiUri}"
    override fun migrate(context: Context?) {
        context!!.connection.createStatement().use { select ->
            select.executeQuery("SELECT cv.id, c.name, cv.version FROM component_version CV JOIN component C ON CV.component_id = C.id")
                .use { rows ->
                    while (rows.next()) {
                        val id = rows.getLong(1)
                        val component = rows.getString(2)
                        val version = rows.getString(3)

                        getComponentBuilds(
                            component,
                            arrayOf(BuildStatus.BUILD, BuildStatus.RC, BuildStatus.RELEASE),
                            arrayOf(version)
                        ).firstOrNull()
                            ?.let { build ->
                                context.connection.createStatement().use { update ->
                                    update.execute("UPDATE component_version SET release_version='${build.releaseVersion}' WHERE id=$id")
                                }
                            } ?: run {
                                context.connection.createStatement().use { delete ->
//                                    delete.execute("DELETE FROM component_version WHERE id=$id")
                                    log.error("No build found: '$component:$version'")
                                }
                        }
                    }
                }
        }
    }

    private fun getComponentBuilds(
        component: String,
        buildStatuses: Array<BuildStatus>,
        versions: Array<String>
    ): List<ComponentBuild> {
        val params = mapOf(
            "build_whitelist" to "status,version,release_version",
            "version_statuses" to buildStatuses.joinToString(separator = ","),
            "versions_field" to "VERSION"
        )

        return versions.toList().chunked(20).flatMap {
            get(
                url = "$relengUrl/components/$component",
                params = params + mapOf("versions" to it.joinToString(","))
            ).toObject(object : TypeReference<ComponentBuilds>() {}).builds
        }
    }

    private fun <T> Response.toObject(typeReference: TypeReference<T>): T {
        if (this.statusCode / 100 != 2) {
            if (this.statusCode == 404) {
                throw NotFoundException(this.text)
            } else {
                throw RuntimeException(this.text)
            }
        }
        return objectMapper.readValue(this.text, typeReference)
    }

    private data class ComponentBuilds(val name: String, val builds: List<ComponentBuild>)

    private data class ComponentBuild(val status: BuildStatus, val version: String, @JsonProperty("release_version")
        val releaseVersion: String)

    companion object {
        private val log = LoggerFactory.getLogger(V20240802154520__component_version_fill_release_version::class.java)
    }
}
