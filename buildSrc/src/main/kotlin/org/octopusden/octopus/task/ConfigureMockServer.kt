package org.octopusden.octopus.task

import com.google.common.net.HttpHeaders
import java.nio.charset.StandardCharsets
import org.apache.http.entity.ContentType
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.json.JSONArray
import org.json.JSONObject
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse


abstract class ConfigureMockServer : DefaultTask() {
    @get:Input
    abstract val host: Property<String>
    @get:Input
    abstract val port: Property<Int>

    private val mockServerClient get() = MockServerClient(host.get(), port.get())

    @TaskAction
    fun configureMockServer() {
        mockServerClient.reset()
        val builds = JSONArray(
            project.rootDir.resolve("test-common").resolve("src").resolve("main").resolve("mockserver")
                .resolve("builds.json").readText(StandardCharsets.UTF_8)
        )
        mockServerClient.`when`(
            HttpRequest.request().withMethod("GET")
                .withPath("/rest/release-engineering/3/component/{component-name}/builds")
                .withPathParameter("component-name")
        ).respond {
            val component = it.getFirstPathParameter("component-name")
            val versions = it.queryStringParameters.getValues("versions")
            val versionStatuses = it.queryStringParameters.getValues("statuses")
            val foundBuilds = builds.filter { build ->
                build as JSONObject
                component == build.getString("component") &&
                        versionStatuses.contains(build.getString("status")) &&
                        versions.contains(build.getString("version"))
            }
            HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                .withStatusCode(200)
                .withBody(JSONArray(foundBuilds.map { build ->
                    build as JSONObject
                    JSONObject().put("component", build.getString("component"))
                        .put("version", build.getString("version"))
                        .put("status", build.getString("status"))
                        .put("hotfix", build.optBoolean("hotfix", false))
                }).toString(2))
        }
        mockServerClient.`when`(
            HttpRequest.request().withMethod("GET")
                .withPath("/rest/release-engineering/3/component/{component-name}/version/{version}/build")
                .withPathParameter("version")
                .withPathParameter("component-name")
        ).respond {
            val component = it.getFirstPathParameter("component-name")
            val version = it.getFirstPathParameter("version")
            val foundBuild = builds.firstOrNull { build ->
                build as JSONObject
                component == build.getString("component") && (
                        version == build.getString("version") || version == build.getString("release_version")
                        )
            } as JSONObject?

            foundBuild?.let { build ->
                HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                    .withStatusCode(200)
                    .withBody(build.toString(2))
            } ?: HttpResponse.response().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                .withStatusCode(404)
                .withBody(
                    JSONObject().put("errorCode", "NOT_FOUND")
                        .put("errorMessage", "Build $component:$version is not found")
                        .toString(2)
                )
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
