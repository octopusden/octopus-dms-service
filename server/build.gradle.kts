buildscript {
    dependencies {
        classpath("com.bmuschko:gradle-docker-plugin:3.6.2")
    }
}

plugins {
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.jpa")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlin.plugin.noarg")
    id("com.bmuschko.docker-spring-boot-application") version "6.4.0"
    id("com.avast.gradle.docker-compose") version "0.14.3"
    `maven-publish`
}

tasks.getByName<Jar>("jar") {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("bootJar") {
            artifact(tasks.getByName("bootJar"))
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

val skipSigning = (System.getenv().getOrDefault("SKIP_SIGNING", project.properties["skip.signing"]) as? String).toBoolean()

signing {
    isRequired = !skipSigning
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["bootJar"])
}

springBoot {
    buildInfo()
}

val dockerRegistry = System.getenv().getOrDefault("DOCKER_REGISTRY", project.properties["docker.registry"]) as? String
val octopusGithubDockerRegistry = System.getenv().getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]) as? String
val authServerUrl = System.getenv().getOrDefault("AUTH_SERVER_URL", project.properties["auth-server.url"]) as? String
val authServerRealm = System.getenv().getOrDefault("AUTH_SERVER_REALM", project.properties["auth-server.realm"]) as? String

docker {
    springBootApplication {
        baseImage.set("$dockerRegistry/openjdk:11")
        ports.set(listOf(8080, 8080))
        images.set(setOf("$octopusGithubDockerRegistry/octopusden/${project.name}:${project.version}"))
    }
}

tasks.getByName("dockerBuildImage").doFirst {
    if (dockerRegistry.isNullOrBlank() || octopusGithubDockerRegistry.isNullOrBlank()) {
        throw IllegalArgumentException(
            "Start gradle build with" +
                    (if (dockerRegistry.isNullOrBlank()) " -Pdocker.registry=..." else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " -Poctopus.github.docker.registry=..." else "") +
                    " or set env variable(s):" +
                    (if (dockerRegistry.isNullOrBlank()) " DOCKER_REGISTRY" else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "")
        )
    }
}

dockerCompose {
    useComposeFiles.add("$projectDir/src/test/docker/docker-compose.yaml")
    waitForTcpPorts = true
    captureContainersOutputToFiles = File("$buildDir/docker_logs")
    environment.putAll(mapOf(
        "DOCKER_REGISTRY" to dockerRegistry,
        "OCTOPUS_GITHUB_DOCKER_REGISTRY" to octopusGithubDockerRegistry,
        "OCTOPUS_COMPONENTS_REGISTRY_SERVICE_VERSION" to project.properties["octopus-components-registry-service.version"]
    ))
}

tasks.getByName("composeUp").doFirst {
    if (dockerRegistry.isNullOrBlank() || octopusGithubDockerRegistry.isNullOrBlank()) {
        throw IllegalArgumentException(
            "Start gradle build with" +
                    (if (dockerRegistry.isNullOrBlank()) " -Pdocker.registry=..." else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " -Poctopus.github.docker.registry=..." else "") +
                    " or set env variable(s):" +
                    (if (dockerRegistry.isNullOrBlank()) " DOCKER_REGISTRY" else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "")
        )
    }
}

dockerCompose.isRequiredBy(tasks["test"])

tasks.named("importArtifactoryDump") {
    dependsOn("composeUp")
}

tasks.named("configureMockServer") {
    dependsOn("composeUp")
}

tasks.withType<Test> {
    dependsOn("importArtifactoryDump")
    dependsOn("configureMockServer")
    doFirst {
        if (authServerUrl.isNullOrBlank() || authServerRealm.isNullOrBlank()) {
            throw IllegalArgumentException(
                "Start gradle build with" +
                        (if (authServerUrl.isNullOrBlank()) " -Pauth-server.url=..." else "") +
                        (if (authServerRealm.isNullOrBlank()) " -Pauth-server.realm=..." else "") +
                        " or set env variable(s):" +
                        (if (authServerUrl.isNullOrBlank()) " AUTH_SERVER_URL" else "") +
                        (if (authServerRealm.isNullOrBlank()) " AUTH_SERVER_REALM" else "")
            )
        }
    }
    environment.putAll(mapOf(
        "AUTH_SERVER_URL" to authServerUrl,
        "AUTH_SERVER_REALM" to authServerRealm
    ))
}

tasks.named("dockerBuildImage") {
    dependsOn("test")
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.retry:spring-retry")
    implementation("io.micrometer:micrometer-registry-prometheus:1.9.5")
    implementation("org.springdoc:springdoc-openapi-ui:1.7.0")
    implementation("org.postgresql:postgresql:${project.properties["postgresql.version"]}")
    implementation("org.flywaydb:flyway-core:8.5.13")
    implementation("org.jfrog.artifactory.client:artifactory-java-client-services:2.13.1")
    implementation("org.danilopianini:khttp:1.2.2")
    implementation("org.octopusden.octopus-cloud-commons:octopus-security-common:2.0.10")
    implementation("org.octopusden.octopus.infrastructure:components-registry-service-client:${project.properties["octopus-components-registry-service.version"]}")
    implementation("org.octopusden.octopus.releng:versions-api:${project.properties["versions-api.version"]}")
    testImplementation(project(":test-common"))
    testImplementation(project(":client"))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }
    testImplementation("org.springframework.security:spring-security-test")
}
