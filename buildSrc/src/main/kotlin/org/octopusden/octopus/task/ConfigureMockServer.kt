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
            HttpRequest.request().withMethod("GET")
                .withPath("/rest/release-engineering/3/component/ee-component/builds")
        ).respond {
            val versions = it.queryStringParameters.getValues("versions")
            val versionStatuses = it.queryStringParameters.getValues("statuses")
            val builds = eeComponentBuilds.filter { build ->
                build as JSONObject
                versionStatuses.contains(build.getString("status")) && versions.contains(build.getString("version"))
            }
            HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                .withBody(
                    JSONArray(builds)
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
            HttpRequest.request().withMethod("GET")
                .withPath("/rest/release-engineering/3/component-management/{component-name}")
                .withPathParameter("component-name")
        ).respond {
            val component = it.getFirstPathParameter("component-name")
            if ("ee-component".equals(component, ignoreCase = true)) {
                HttpResponse.response().withStatusCode(200).withBody(
                    JSONObject().put("id", component)
                        .toString(2)
                )
            } else {
                HttpResponse.response().withStatusCode(404).withBody(
                    JSONObject().put("errorCode", "NOT_FOUND")
                        .put("errorMessage", "Component $component not registered in RM2.0 base")
                        .toString(2)
                )
            }
        }
        mockServerClient.`when`(
            HttpRequest.request().withMethod("GET")
                .withPath("/rest/release-engineering/3/component/{component-name}/version/{version}/build")
                .withPathParameter("version")
                .withPathParameter("component-name")
        ).respond {
            val version = it.getFirstPathParameter("version")
            val component = it.getFirstPathParameter("component-name")
            val build = eeComponentBuilds.firstOrNull { build ->
                build as JSONObject
                version == build.getString("version") || version == build.getString("release_version")
            } as JSONObject?

            build?.let {
                HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                    .withStatusCode(200)
                    .withBody(
                        JSONObject().put("component", component)
                            .put("version", build.getString("version"))
                            .put("status", build.getString("status"))
                            .put("dependencies", build.getJSONArray("dependencies"))
                            .put("commits", build.getJSONArray("commits"))
                            .put("statusHistory", build.getJSONObject("statusHistory"))
                            .toString(2)
                    )
            } ?: HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                .withStatusCode(404)
                .withBody(
                    JSONObject().put("errorCode", "NOT_FOUND")
                        .put("errorMessage", "Build $component:$version is not found")
                        .toString(2)
                )
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
