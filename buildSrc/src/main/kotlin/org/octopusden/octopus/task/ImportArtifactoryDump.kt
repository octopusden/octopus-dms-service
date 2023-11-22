package org.octopusden.octopus.task

import java.io.File
import khttp.get
import khttp.post
import khttp.structures.authorization.BasicAuthorization
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ImportArtifactoryDump : DefaultTask() {
    @get:Input
    abstract var retryLimit: Int

    @TaskAction
    fun importArtifactoryDump() {
        val dump = project.rootDir.resolve("test-common").resolve("src")
            .resolve("main").resolve("artifactory").resolve("dump")
        val latest = dump.list { dir, name -> dir == dump && File(dir, name).isDirectory }?.maxOrNull()
        if (latest == null) {
            project.logger.info("No dump found. Skipping import")
        } else {
            project.logger.info("Importing dump $latest")
            var retryCounter = 0
            while (retryCounter < retryLimit) {
                retryCounter++
                Thread.sleep(5000)
                if (get(url = "http://localhost:8081/artifactory/api/system/ping").statusCode == 200) {
                    break
                }
            }
            project.logger.info(
                post(
                    url = "http://localhost:8081/artifactory/api/import/system",
                    auth = BasicAuthorization("admin", "password"),
                    json = mapOf(
                        "importPath" to "/dump/$latest",
                        "includeMetadata" to true,
                        "verbose" to true,
                        "failOnError" to true,
                        "failIfEmpty" to true
                    )
                ).text
            )
        }
    }
}