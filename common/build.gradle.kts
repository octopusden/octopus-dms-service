import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
}

val javaVersion = JavaVersion.VERSION_1_8

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = javaVersion.toString()
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.withType<JavaCompile> {
    options.release.set(8)
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
    api(platform("com.fasterxml.jackson:jackson-bom:${project.properties["jackson.version"]}"))
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.19")
}
