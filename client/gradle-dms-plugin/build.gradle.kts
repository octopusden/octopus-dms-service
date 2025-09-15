plugins {
    groovy
    `java-gradle-plugin`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

val pluginId = "org.octopusden.octopus-dms"

publishing {
    publications {
        withType(MavenPublication::class.java) {
            pom {
                name.set(pluginId)
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
            displayName = "Octopus DMS plugin"
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