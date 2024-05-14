package org.octopusden.octopus.task

import com.google.common.net.HttpHeaders
import java.nio.charset.StandardCharsets
import org.apache.http.entity.ContentType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.json.JSONArray
import org.json.JSONObject
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse


abstract class ConfigureMockServer : DefaultTask() {
    private val mockServerClient = MockServerClient("localhost", 1080)

    @TaskAction
    fun configureMockServer() {
        mockServerClient.reset()
        val eeComponentBuilds = JSONArray(
            project.rootDir.resolve("test-common").resolve("src").resolve("main").resolve("mockserver")
                .resolve("ee-component-builds.json").readText(StandardCharsets.UTF_8)
        )
        mockServerClient.`when`(
            HttpRequest.request().withMethod("GET").withPath("/rest/release-engineering/3/components/ee-component")
                .withQueryStringParameter("build_whitelist", "status,version,release_version")
        ).respond {
            val versions = it.getFirstQueryStringParameter("versions").split(',')
            val versionsField = it.getFirstQueryStringParameter("versions_field")
            val versionStatuses = it.getFirstQueryStringParameter("version_statuses").split(',')
            val builds = eeComponentBuilds.filter { build ->
                build as JSONObject
                versionStatuses.contains(build.getString("status")) && when (versionsField) {
                    "VERSION" -> versions.contains(build.getString("version"))
                    "RELEASE_VERSION" -> versions.contains(build.getString("release_version"))
                    else -> false
                }
            }
            HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                .withBody(
                    JSONObject().put("name", "ee-component")
                        .put("builds", builds)
                        .toString(2)
                ).withStatusCode(200)
        }
        mockServerClient.`when`(
            HttpRequest.request().withMethod("GET").withPath("/rest/release-engineering/3/components/some-ee-component")
                .withQueryStringParameter("build_whitelist", "status,version,release_version")
        ).respond {
            val versions = it.getFirstQueryStringParameter("versions").split(',')
            val versionsField = it.getFirstQueryStringParameter("versions_field")
            val versionStatuses = it.getFirstQueryStringParameter("version_statuses").split(',')
            val builds = eeComponentBuilds.filter { build ->
                build as JSONObject
                versionStatuses.contains(build.getString("status")) && when (versionsField) {
                    "VERSION" -> versions.contains(build.getString("version"))
                    "RELEASE_VERSION" -> versions.contains(build.getString("release_version"))
                    else -> false
                }
            }
            HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                .withBody(
                    JSONObject().put("name", "some-ee-component")
                        .put("builds", builds)
                        .toString(2)
                ).withStatusCode(200)
        }
        mockServerClient.`when`(
            HttpRequest.request().withMethod("GET").withPath("/rest/release-engineering/3/component-management/component/{component-name}")
                .withPathParameter("component-name")
        ).respond {
            val component = it.getFirstPathParameter("component-name")
            if ("ee-component".equals(component, ignoreCase = true)) {
                HttpResponse.response().withStatusCode(200)
            } else {
                HttpResponse.response().withStatusCode(404)
            }
        }
        mockServerClient.`when`(
            HttpRequest.request().withMethod("GET").withPath("/rest/release-engineering/3/component/{component-name}/version/{version}/status")
                .withPathParameter("version")
                .withPathParameter("component-name")
        ).respond {
            val version = it.getFirstPathParameter("version")
            val component = it.getFirstPathParameter("component-name")
            val build = eeComponentBuilds.firstOrNull { build ->
                build as JSONObject
                version == build.getString("version")
            } as JSONObject?
            if (build != null) {
                HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                    .withBody(
                        JSONObject().put("component", component)
                            .put("version", build.getString("version"))
                            .put("buildVersion", build.getString("version"))
                            .put("releaseVersion", build.getString("release_version"))
                            .put("versionStatus", build.getString("status"))
                            .toString(2)
                    ).withStatusCode(200)
            } else {
                HttpResponse.response().withStatusCode(404)
            }
        }
        mockServerClient.`when`(
            HttpRequest.request().withMethod("POST").withPath("/webhook")
        ).respond {
            if (it.bodyAsString?.contains("ee-component") == true) {
                HttpResponse.response().withStatusCode(200)
            } else {
                HttpResponse.response().withStatusCode(500)
            }
        }
    }
}
