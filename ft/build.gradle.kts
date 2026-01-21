import org.octopusden.octopus.task.ConfigureMockServer
import org.octopusden.octopus.task.ImportArtifactoryDump

plugins {
    id("java-gradle-plugin")
    id("org.octopusden.octopus.oc-template")
}

fun String.getExt() = project.ext[this] as String

sourceSets {
    create("ft") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

tasks {
    val importArtifactoryDump by registering(ImportArtifactoryDump::class)
    val configureMockServer by registering(ConfigureMockServer::class)
}
val commonOkdParameters = mapOf(
    "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
    "DOCKER_REGISTRY" to "dockerRegistry".getExt()
)
fun String.getPort() = when (this) {
    "artifactory" -> 8081
    "comp-reg" -> 4567
    "mockserver" -> 1080
    "rm" -> 8083
    "dms-postgres" -> 5432
    "gateway" -> 8765
    "dms-service" -> 8080
    "artifactory-postgres" -> 5432
    else -> throw Exception("Unknown service '$this'")
}
fun getOkdInternalHost(serviceName: String) = "${ocTemplate.getPod(serviceName)}-service:${serviceName.getPort()}"

val ftImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

ftImplementation.isCanBeResolved = true

configurations["ftRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

tasks.named("ocCreate") {
    dependsOn(
        ":maven-dms-plugin:publishToMavenLocal", ":gradle-dms-client:publishToMavenLocal",
        ":gradle-dms-plugin:publishToMavenLocal", ":dms-service:dockerPushImage"
    )
}

ocTemplate{
    workDir.set(layout.buildDirectory.dir("okd"))
    clusterDomain.set("okdClusterDomain".getExt())
    namespace.set("okdProject".getExt())
    prefix.set("dms-ft")

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
            "APPLICATION_DEV_CONTENT" to layout.projectDirectory.dir("../test-common/src/main/config/components-registry-service.yaml").asFile.readText()
        ))
    }

    service("rm") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/release-management.yaml"))
        parameters.set(mapOf(
            "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
            "RELEASE_MANAGEMENT_SERVICE_VERSION" to properties["octopus-release-management-service.version"] as String,
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "APPLICATION_DEV_CONTENT" to layout.projectDirectory.dir("../test-common/src/main/config/release-management-service.yaml").asFile.readText(),
            "TEST_MOCK_SERVER_HOST" to getOkdInternalHost("mockserver")
        ))
        dependsOn.set(listOf("mockserver"))
    }

    service("dms-postgres") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/postgres.yaml"))
        parameters.set(
            commonOkdParameters + mapOf(
                "POSTGRES_NAME" to "dms-postgres",
                "POSTGRES_IMAGE_TAG" to project.properties["dms-postgres.version"] as String,
                "POSTGRES_DB" to project.properties["dms-postgres.db"] as String,
                "POSTGRES_USER" to project.properties["dms-postgres.user"] as String,
                "POSTGRES_PASSWORD" to project.properties["dms-postgres.password"] as String,
                "POSTGRES_STORAGE" to project.properties["dms-postgres.storage"] as String,
            )
        )
    }

    service("artifactory-postgres") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/postgres.yaml"))
        parameters.set(
            commonOkdParameters + mapOf(
                "POSTGRES_NAME" to "artifactory-postgres",
                "POSTGRES_IMAGE_TAG" to project.properties["artifactory-postgres.version"] as String,
                "POSTGRES_DB" to project.properties["artifactory-postgres.db"] as String,
                "POSTGRES_USER" to project.properties["artifactory-postgres.user"] as String,
                "POSTGRES_PASSWORD" to project.properties["artifactory-postgres.password"] as String,
                "POSTGRES_STORAGE" to project.properties["artifactory-postgres.storage"] as String,
            )
        )
        dependsOn.set(listOf("dms-postgres"))
    }

    service("artifactory") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/artifactory.yaml"))
        parameters.set(mapOf(
            "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
            "ARTIFACTORY_IMAGE_TAG" to project.properties["artifactory.image-tag"] as String,
            "POSTGRES_DB" to project.properties["artifactory-postgres.db"] as String,
            "POSTGRES_USER" to project.properties["artifactory-postgres.user"] as String,
            "POSTGRES_PASSWORD" to project.properties["artifactory-postgres.password"] as String,
            "POSTGRES_HOST" to getOkdInternalHost("artifactory-postgres")
            ))
        dependsOn.set(listOf("artifactory-postgres"))
    }

    service("gateway") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/api-gateway.yaml"))
        parameters.set(mapOf(
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
            "APPLICATION_DEV_CONTENT" to layout.projectDirectory.dir("./src/ft/config/api-gateway.yaml").asFile.readText(),
            "API_GATEWAY_VERSION" to properties["api-gateway.version"] as String,
            "AUTH_SERVER_URL" to "authServerUrl".getExt(),
            "AUTH_SERVER_REALM" to "authServerRealm".getExt(),
            "AUTH_SERVER_CLIENT_ID" to "authServerClientId".getExt(),
            "AUTH_SERVER_CLIENT_SECRET" to "authServerClientSecret".getExt(),
            "TEST_DMS_SERVICE_HOST" to getOkdInternalHost("dms-service"),
            "TEST_API_GATEWAY_HOST_EXTERNAL" to ocTemplate.getOkdHost("gateway")
        ))
        dependsOn.set(listOf("dms-service"))
    }

    service("dms-service") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/dms-service.yaml"))
        parameters.set(mapOf(
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
            "APPLICATION_DEV_CONTENT" to layout.projectDirectory.dir("./src/ft/config/dms-service.yaml").asFile.readText(),
            "DMS_SERVICE_VERSION" to version as String,
            "AUTH_SERVER_URL" to "authServerUrl".getExt(),
            "AUTH_SERVER_REALM" to "authServerRealm".getExt(),
            "TEST_API_GATEWAY_HOST_EXTERNAL" to ocTemplate.getOkdHost("gateway"),
            "TEST_POSTGRES_HOST" to getOkdInternalHost("dms-postgres"),
            "TEST_ARTIFACTORY_HOST" to getOkdInternalHost("artifactory"),
            "TEST_ARTIFACTORY_HOST_EXTERNAL" to ocTemplate.getOkdHost("artifactory"),
            "TEST_COMPONENTS_REGISTRY_HOST" to getOkdInternalHost("comp-reg"),
            "TEST_RELEASE_MANAGEMENT_HOST" to getOkdInternalHost("rm"),
            "TEST_MOCK_SERVER_HOST" to getOkdInternalHost("mockserver")

        ))
        dependsOn.set(listOf("dms-postgres", "artifactory", "comp-reg", "rm", "mockserver"))
    }
}

val copyArtifactoryDump = tasks.register<Exec>("copyArtifactoryDump") {
    val localFile = layout.projectDirectory.dir("../test-common/src/main/artifactory/dump").asFile.absolutePath
    commandLine(
        "oc", "cp",
        "$localFile/.",
        "-n", "okdProject".getExt(),
        "${ocTemplate.getPod("artifactory")}:/opt/jfrog/artifactory/var/etc/artifactory/import"
    )

    dependsOn("ocCreate")
}

tasks.named<ConfigureMockServer>("configureMockServer") {
    host.set(ocTemplate.getOkdHost("mockserver"))
    port.set(80)
    dependsOn("ocCreate")
}

tasks.named<ImportArtifactoryDump>("importArtifactoryDump") {
    host.set(ocTemplate.getOkdHost("artifactory"))
    retryLimit.set(30)
    importPath.set("/opt/jfrog/artifactory/var/etc/artifactory/import")
    dependsOn(copyArtifactoryDump)
}

tasks.register("waitPostgresExternalIP") {
    dependsOn("ocCreate")
    doLast{
        val ns = "okdProject".getExt()
        val deploymentPrefix = "${ocTemplate.prefix.get()}-${project.version}".lowercase().replace(Regex("[^-a-z0-9]"), "-")
        val svc = "$deploymentPrefix-dms-postgres-service"
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

val ft by tasks.creating(Test::class) {
    systemProperties.putAll(mapOf(
        "dms-service.version" to project.version,
        "dms-service.user" to "dmsServiceUser".getExt(),
        "dms-service.password" to "dmsServicePassword".getExt()
    ))
    group = "verification"
    description = "Runs the functional tests"
    testClassesDirs = sourceSets["ft"].output.classesDirs
    classpath = sourceSets["ft"].runtimeClasspath
    dependsOn("importArtifactoryDump", "configureMockServer")
    ocTemplate.isRequiredBy(this)
    systemProperties["test.artifactory-host"] = ocTemplate.getOkdHost("artifactory")
    systemProperties["test.components-registry-host"] = ocTemplate.getOkdHost("comp-reg")
    systemProperties["test.api-gateway-host"] = ocTemplate.getOkdHost("gateway")
    dependsOn("waitPostgresExternalIP")
    doFirst {
        systemProperties["test.postgres-host"] = "postgresExternalIp".getExt()
    }
}

idea.module {
    scopes["PROVIDED"]?.get("plus")?.add(configurations["ftImplementation"])
}

dependencies {
    ftImplementation(project(":common"))
    ftImplementation(project(":test-common"))
    ftImplementation(project(":client"))
    ftImplementation(gradleTestKit())

    ftImplementation(platform("org.junit:junit-bom:${project.properties["junit.version"]}"))
    ftImplementation("org.junit.jupiter:junit-jupiter-engine")
    ftImplementation("org.junit.jupiter:junit-jupiter-params")
}
