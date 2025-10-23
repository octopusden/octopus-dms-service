import org.octopusden.octopus.task.ConfigureMockServer
import org.octopusden.octopus.task.ImportArtifactoryDump

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
    id("org.octopusden.octopus.oc-template")
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

tasks {
    val importArtifactoryDump by registering(ImportArtifactoryDump::class)
    val configureMockServer by registering(ConfigureMockServer::class)
}

fun String.getExt() = project.ext[this] as String
fun String.getPort() = when (this) {
    "artifactory" -> 8081
    "comp-reg" -> 4567
    "mockserver" -> 1080
    "rm" -> 8083
    "postgres" -> 5432
    else -> throw Exception("Unknown service '$this'")
}
fun getOkdInternalHost(serviceName: String) = "${ocTemplate.getPod(serviceName)}-service:${serviceName.getPort()}"

val commonOkdParameters = mapOf(
    "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
    "DOCKER_REGISTRY" to "dockerRegistry".getExt()
)

docker {
    springBootApplication {
        baseImage.set("${"dockerRegistry".getExt()}/eclipse-temurin:21-jdk")
        ports.set(listOf(8080))
        images.set(setOf("${"octopusGithubDockerRegistry".getExt()}/octopusden/${project.name}:${project.version}"))
    }
}

dockerCompose {
    useComposeFiles.add("$projectDir/src/test/docker/docker-compose.yaml")
    waitForTcpPorts = true
    captureContainersOutputToFiles = layout.buildDirectory.dir("docker-logs").get().asFile
    environment.putAll(mapOf(
        "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
        "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
        "OCTOPUS_COMPONENTS_REGISTRY_SERVICE_VERSION" to project.properties["octopus-components-registry-service.version"],
        "OCTOPUS_RELEASE_MANAGEMENT_SERVICE_VERSION" to project.properties["octopus-release-management-service.version"],
        "MOCK_SERVER_VERSION" to project.properties["mockserver.version"],
        "POSTGRES_IMAGE_TAG" to project.properties["postgres.image-tag"],
        "ARTIFACTORY_IMAGE_TAG" to project.properties["artifactory.image-tag"],
        "TEST_MOCK_SERVER_HOST" to "mockserver:1080"
    ))
}

ocTemplate{
    workDir.set(layout.buildDirectory.dir("okd"))
    clusterDomain.set("okdClusterDomain".getExt())
    namespace.set("okdProject".getExt())
    prefix.set("dms-ut")

    "okdWebConsoleUrl".getExt().takeIf { it.isNotBlank() }?.let{
        webConsoleUrl.set(it)
    }

    service("mockserver") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/mockserver.yaml"))
        parameters.set(commonOkdParameters + mapOf(
            "MOCK_SERVER_VERSION" to properties["mockserver.version"] as String
        ))
    }

    service("comp-reg") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/components-registry.yaml"))
        val componentsRegistryWorkDir = layout.projectDirectory.dir("../test-common/src/main/components-registry").asFile.absolutePath
        parameters.set(commonOkdParameters + mapOf(
            "COMPONENTS_REGISTRY_SERVICE_VERSION" to properties["octopus-components-registry-service.version"] as String,
            "AGGREGATOR_GROOVY_CONTENT" to file("${componentsRegistryWorkDir}/Aggregator.groovy").readText(),
            "DEFAULTS_GROOVY_CONTENT" to file("${componentsRegistryWorkDir}/Defaults.groovy").readText(),
            "TEST_COMPONENTS_GROOVY_CONTENT" to file("${componentsRegistryWorkDir}/TestComponents.groovy").readText(),
            "APPLICATION_DEV_CONTENT" to layout.projectDirectory.dir("src/test/docker/components-registry-service.yaml").asFile.readText()
        ))
    }

    service("rm") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/release-management.yaml"))
        parameters.set(commonOkdParameters + mapOf(
            "RELEASE_MANAGEMENT_SERVICE_VERSION" to properties["octopus-release-management-service.version"] as String,
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "APPLICATION_DEV_CONTENT" to layout.projectDirectory.dir("src/test/docker/release-management-service.yaml").asFile.readText(),
            "TEST_MOCK_SERVER_HOST" to getOkdInternalHost("mockserver")
        ))
    }

    service("artifactory") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/artifactory.yaml"))
        parameters.set(commonOkdParameters + mapOf(
            "ARTIFACTORY_IMAGE_TAG" to project.properties["artifactory.image-tag"] as String,
            "SERVICE_ACCOUNT_ANYUID" to project.properties["okd.service-account-anyuid"] as String
        ))
    }

    service("postgres") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/postgres.yaml"))
        parameters.set(commonOkdParameters + mapOf(
            "POSTGRES_IMAGE_TAG" to project.properties["postgres.image-tag"] as String
        ))
    }
}

val copyArtifactoryDump = tasks.register<Exec>("copyArtifactoryDump") {
    val localFile = layout.projectDirectory.dir("../test-common/src/main/artifactory/dump").asFile.absolutePath
    commandLine("oc", "cp", localFile, "-n", "okdProject".getExt(),
        "${ocTemplate.getPod("artifactory")}:/")
    dependsOn("ocCreate")
}

tasks.named<ConfigureMockServer>("configureMockServer") {
    when ("testPlatform".getExt()) {
        "okd" -> {
            host.set(ocTemplate.getOkdHost("mockserver"))
            port.set(80)
            dependsOn("ocCreate")
        }
        "docker" -> {
            host.set("localhost")
            port.set(1080)
            dependsOn("composeUp")
        }
    }
}

tasks.named<ImportArtifactoryDump>("importArtifactoryDump") {
    when ("testPlatform".getExt()) {
        "okd" -> {
            host.set(ocTemplate.getOkdHost("artifactory"))
            retryLimit.set(3)
            dependsOn(copyArtifactoryDump)
        }
        "docker" -> {
            host.set("localhost:8081")
            retryLimit.set(3)
            dependsOn("composeUp")
        }
    }

}

tasks.register("waitPostgresExternalIP") {
    doLast{
        val ns = "okdProject".getExt()
        val deploymentPrefix = "${ocTemplate.prefix.get()}-${project.version}".lowercase().replace(Regex("[^-a-z0-9]"), "-")
        val svc = "$deploymentPrefix-postgres-service"
        val timeoutMs = 5 * 60 * 1000
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            println("Wait external IP for $svc ...")
            val proc = ProcessBuilder("oc", "-n", ns, "get", "svc", svc, "-o", "jsonpath={.status.loadBalancer.ingress[0].ip}").start()
            val result = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (result.isNotBlank() && result != "<pending>") {
                println("$svc is ready: $result")
                project.ext["postgresExternalIp"] = result
                return@doLast
            }
            Thread.sleep(5000)
        }
        throw GradleException("The waiting time is over!")
    }
}

tasks.withType<Test> {
    dependsOn("importArtifactoryDump", "configureMockServer")
    when ("testPlatform".getExt()) {
        "okd" -> {
            ocTemplate.isRequiredBy(this)
            systemProperties["test.artifactory-host"] = ocTemplate.getOkdHost("artifactory")
            systemProperties["test.components-registry-host"] = ocTemplate.getOkdHost("comp-reg")
            systemProperties["test.mock-server-host"] = ocTemplate.getOkdHost("mockserver")
            systemProperties["test.release-management-host"] = ocTemplate.getOkdHost("rm")
            dependsOn("waitPostgresExternalIP")
            doFirst {
                systemProperties["test.postgres-host"] = "postgresExternalIp".getExt()
            }
        }
        "docker" -> {
            dockerCompose.isRequiredBy(this)
            systemProperties["test.postgres-host"] = "localhost:5432"
            systemProperties["test.artifactory-host"] = "localhost:8081"
            systemProperties["test.components-registry-host"] = "localhost:4567"
            systemProperties["test.mock-server-host"] = "localhost:1080"
            systemProperties["test.release-management-host"] = "localhost:8083"
        }
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
