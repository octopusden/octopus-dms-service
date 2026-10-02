plugins {
    groovy
    `java-gradle-plugin`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

// The plugin's classes are Groovy: hold them to Java 8 bytecode as well. Otherwise GroovyCompile
// follows the project-wide targetCompatibility (21), and consumers on a Java 8 build cannot load it.
tasks.withType<GroovyCompile>().configureEach {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

val pluginId = "org.octopusden.octopus-dms"
val pluginDisplayName = "Octopus DMS plugin"

publishing {
    publications {
        withType(MavenPublication::class.java) {
            // The plugin marker is named after the plugin's display name, the implementation after
            // the plugin id. Set explicitly: which of this block and java-gradle-plugin names the
            // marker last depends on the Gradle version.
            val pomName = if (name.endsWith("PluginMarkerMaven")) pluginDisplayName else pluginId
            pom {
                name.set(pomName)
                description.set("Octopus module: ${project.name}")
                url.set("https://github.com/octopusden/octopus-dms-service.git")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/kzaporozhtsev/octopus-dms-service.git")
                    connection.set("scm:git://github.com/octopusden/octopus-dms-service.git")
                }
                developers {
                    developer {
                        id.set("octopus")
                        name.set("octopus")
                    }
                }
            }
        }
    }
}

gradlePlugin {
    plugins {
        create(project.name) {
            id = pluginId
            displayName = pluginDisplayName
            description = "Octopus module: ${project.name}"
            implementationClass = "org.octopusden.octopus.dms.client.DmsPlugin"
        }
    }
}

signing {
    isRequired = project.ext["signingRequired"] as Boolean
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}

dependencies {
    api(project(":client"))
    implementation(gradleApi())
}
