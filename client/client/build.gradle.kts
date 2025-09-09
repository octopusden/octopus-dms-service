import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
}

kotlin {
    jvmToolchain(8)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
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
    api(project(":common"))

    api(platform("org.springframework.cloud:spring-cloud-dependencies:${project.properties["spring-cloud-legacy.version"]}"))
    api("io.github.openfeign:feign-core")
    api("io.github.openfeign:feign-httpclient")
    api("io.github.openfeign:feign-jackson")
    api("io.github.openfeign:feign-slf4j")
    api("io.github.openfeign.form:feign-form:3.8.0")

    api(platform("com.fasterxml.jackson:jackson-bom:${project.properties["jackson.version"]}"))
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("com.fasterxml.jackson.core:jackson-core")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("commons-logging:commons-logging:1.2")
    implementation("org.apache.httpcomponents:httpmime:4.5.13")
}

project.tasks.publishToMavenLocal {
    dependsOn(":common:publishToMavenLocal")
}
