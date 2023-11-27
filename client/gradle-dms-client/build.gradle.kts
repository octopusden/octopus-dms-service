plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
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

signing {
    isRequired = project.ext["signingRequired"] as Boolean
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}

dependencies {
    implementation(project(":client"))
    implementation("org.gradle:gradle-core:1.6")
    implementation("org.gradle:gradle-tooling-api:2.6")
    implementation("org.codehaus.groovy:groovy-all:2.4.15")
    implementation("org.slf4j:slf4j-api")
    implementation("commons-io:commons-io:2.2")
    implementation("org.octopusden.octopus.infrastructure:components-registry-service-client:${project.properties["octopus-components-registry-service.version"]}")
    implementation("org.octopusden.octopus.releng:versions-api:${project.properties["versions-api.version"]}")
}
