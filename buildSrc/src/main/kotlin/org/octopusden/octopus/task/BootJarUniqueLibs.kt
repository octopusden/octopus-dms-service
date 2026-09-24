package org.octopusden.octopus.task

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.io.File

/**
 * Resolves JAR filename collisions when building a Spring Boot executable JAR.
 *
 * Different dependencies may produce JARs with identical filenames.
 * For example, DMS and RMS both publish common-2.1.2.jar under different
 * Maven groups. Spring Boot packages dependencies into BOOT-INF/lib using
 * their filenames, which causes bootJar to fail on duplicate entries.
 */
fun BootJar.configureUniqueLibs(
    runtimeClasspath: Configuration,
) {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    val projectGroups = project.rootProject.allprojects
        .associate { it.path to it.group.toString() }

    var jarRenames: Map<File, String> = emptyMap()

    doFirst {
        val jars = classpath.files
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .distinctBy { it.canonicalFile }

        val conflicts = jars
            .groupBy { it.name }
            .filterValues { files -> files.size > 1 }

        if (conflicts.isEmpty()) {
            jarRenames = emptyMap()
            return@doFirst
        }

        val artifactsByFile = runtimeClasspath.incoming.artifacts.artifacts
            .associateBy { it.file.canonicalFile }

        val usedNames = jars
            .filter { file -> file.name !in conflicts.keys }
            .mapTo(mutableSetOf()) { file -> file.name }

        val replacements = mutableMapOf<File, String>()

        conflicts.forEach { (originalName, files) ->
            files.forEach { file ->
                val canonicalFile = file.canonicalFile
                val component = artifactsByFile[canonicalFile]?.id?.componentIdentifier
                val prefix = when (component) {
                    is ModuleComponentIdentifier -> component.group
                    is ProjectComponentIdentifier -> projectGroups[component.projectPath] ?: "project"
                    else -> "file"
                }.replace(
                    Regex("[^A-Za-z0-9._-]"),
                    "-"
                )

                val candidate = "$prefix-$originalName"

                check(usedNames.add(candidate)) {
                    "Cannot resolve bootJar JAR filename collision: $candidate"
                }
                replacements[canonicalFile] = candidate
            }
        }
        jarRenames = replacements

        logger.lifecycle("Renaming conflicting bootJar dependencies:")

        replacements.forEach { (source, target) ->
            logger.lifecycle(" - ${source.name} -> $target")
        }
    }
    rootSpec.filesMatching(
        "**/*.jar",
        Action<FileCopyDetails> { details ->
            if (details.path.startsWith("BOOT-INF/lib/")) {
                jarRenames[details.file.canonicalFile]?.let { newName ->
                    details.name = newName
                }
            }
        }
    )
}