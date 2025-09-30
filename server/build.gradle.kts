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
    id("com.bmuschko.docker-spring-boot-application") version "9.4.0"
    id("com.avast.gradle.docker-compose") version "0.16.9"
    id("com.github.node-gradle.node") version "7.0.2"
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

signing {
    isRequired = project.ext["signingRequired"] as Boolean
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["bootJar"])
}

springBoot {
    buildInfo()
}

@Suppress("UNCHECKED_CAST")
val extValidateFun = project.ext["validateFun"] as ((List<String>) -> Unit)
fun String.getExt() = project.ext[this] as? String

docker {
    springBootApplication {
        baseImage.set("${"dockerRegistry".getExt()}/eclipse-temurin:21-jdk")
        ports.set(listOf(8080))
        images.set(setOf("${"octopusGithubDockerRegistry".getExt()}/octopusden/${project.name}:${project.version}"))
    }
}

tasks.named("dockerPushImage") {
    doFirst {
        println("Waiting for 60 seconds before pushing the image...")
        Thread.sleep(60_000) // 60 секунд
    }
}

tasks.getByName("dockerBuildImage").doFirst {
    extValidateFun.invoke(listOf("dockerRegistry", "octopusGithubDockerRegistry"))
}

dockerCompose {
    useComposeFiles.add("$projectDir/src/test/docker/docker-compose.yaml")
    waitForTcpPorts = true
    captureContainersOutputToFiles = layout.buildDirectory.dir("docker-logs").get().asFile
    environment.putAll(mapOf(
        "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
        "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
        "OCTOPUS_COMPONENTS_REGISTRY_SERVICE_VERSION" to project.properties["octopus-components-registry-service.version"],
        "OCTOPUS_RELEASE_MANAGEMENT_SERVICE_VERSION" to project.properties["octopus-release-management-service.version"]
    ))
}

tasks.getByName("composeUp").doFirst {
    extValidateFun.invoke(listOf("dockerRegistry", "octopusGithubDockerRegistry"))
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
        extValidateFun.invoke(listOf("authServerUrl", "authServerRealm"))
    }
    environment.putAll(mapOf(
        "AUTH_SERVER_URL" to "authServerUrl".getExt(),
        "AUTH_SERVER_REALM" to "authServerRealm".getExt()
    ))
}

tasks.named("dockerBuildImage") {
    dependsOn("test")
}

val npmBuild = tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
    npmCommand.set(listOf("run", "build"))
}

tasks.withType<ProcessResources> {
    dependsOn(npmBuild)
}

node {
    version.set("16.20.2")
    download.set(true)
    npmVersion.set("8.19.4")
}

tasks.getByName<Delete>("clean") {
    this.delete.add("$projectDir/node_modules")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    args(
        "--spring.cloud.config.enabled=false",
        "--spring.profiles.active=dev",
        "--spring.config.additional-location=dev/"
    )
    sourceResources(sourceSets.main.get())
}

dependencies {
    constraints {
        add("implementation", "org.slf4j:slf4j-api:2.0.12")
        add("implementation", "com.zaxxer:HikariCP:5.1.0")
    }

    implementation(project(":common"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.properties["spring-boot.version"]}"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-security")

    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${project.properties["spring-cloud.version"]}"))
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    implementation("org.springframework.retry:spring-retry")

    implementation("org.octopusden.octopus-cloud-commons:octopus-security-common:${project.properties["octopus-cloud-commons.version"]}")
    implementation("org.octopusden.octopus.infrastructure:components-registry-service-client:${project.properties["octopus-components-registry-service.version"]}")
    implementation("org.octopusden.octopus.releng:versions-api:${project.properties["versions-api.version"]}")
    implementation("org.octopusden.octopus.release-management-service:client:${rootProject.properties["octopus-release-management-service.version"]}")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    implementation("org.postgresql:postgresql:${project.properties["postgresql.version"]}")
    implementation("org.flywaydb:flyway-core:9.22.3")
    implementation("org.jfrog.artifactory.client:artifactory-java-client-services:2.13.1")
    implementation("org.danilopianini:khttp:1.2.2")

    testImplementation(project(":test-common"))
    testImplementation(project(":client"))

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.apache.httpcomponents:httpmime:4.5.13")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
}
