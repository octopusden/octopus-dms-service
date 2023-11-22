plugins {
    id("com.avast.gradle.docker-compose") version "0.14.3"
    id("java-gradle-plugin")
}

val dockerRegistry = System.getenv().getOrDefault("DOCKER_REGISTRY", project.properties["docker.registry"]) as? String
val octopusGithubDockerRegistry = System.getenv().getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]) as? String
val authServerUrl = System.getenv().getOrDefault("AUTH_SERVER_URL", project.properties["auth-server.url"]) as? String
val authServerRealm = System.getenv().getOrDefault("AUTH_SERVER_REALM", project.properties["auth-server.realm"]) as? String
val authServerClientId = System.getenv().getOrDefault("AUTH_SERVER_CLIENT_ID", project.properties["auth-server.client-id"]) as? String
val authServerClientSecret = System.getenv().getOrDefault("AUTH_SERVER_CLIENT_SECRET", project.properties["auth-server.client-secret"]) as? String
val dmsServiceUser = System.getenv().getOrDefault("DMS_SERVICE_USER", project.properties["dms-service.user"]) as? String
val dmsServicePassword = System.getenv().getOrDefault("DMS_SERVICE_PASSWORD", project.properties["dms-service.password"]) as? String

dockerCompose {
    useComposeFiles.add("${projectDir}/src/ft/docker/docker-compose.yaml")
    waitForTcpPorts = true
    captureContainersOutputToFiles = File("$buildDir${File.separator}docker_logs")
    environment.putAll(
        mapOf(
            "DMS_SERVICE_VERSION" to project.version,
            "OCTOPUS_COMPONENTS_REGISTRY_SERVICE_VERSION" to project.properties["octopus-components-registry-service.version"],
            "DOCKER_REGISTRY" to dockerRegistry,
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to octopusGithubDockerRegistry,
            "AUTH_SERVER_URL" to authServerUrl,
            "AUTH_SERVER_REALM" to authServerRealm,
            "AUTH_SERVER_CLIENT_ID" to authServerClientId,
            "AUTH_SERVER_CLIENT_SECRET" to authServerClientSecret
        )
    )
}

tasks.getByName("composeUp").doFirst {
    if (dockerRegistry.isNullOrBlank() || octopusGithubDockerRegistry.isNullOrBlank() ||
        authServerUrl.isNullOrBlank() || authServerRealm.isNullOrBlank() ||
        authServerClientId.isNullOrBlank() || authServerClientSecret.isNullOrBlank()
    ) {
        throw IllegalArgumentException(
            "Start gradle build with" +
                    (if (dockerRegistry.isNullOrBlank()) " -Pdocker.registry=..." else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " -Poctopus.github.docker.registry=..." else "") +
                    (if (authServerUrl.isNullOrBlank()) " -Pauth-server.url=..." else "") +
                    (if (authServerRealm.isNullOrBlank()) " -Pauth-server.realm=..." else "") +
                    (if (authServerClientId.isNullOrBlank()) " -Pauth-server.client-id=..." else "") +
                    (if (authServerClientSecret.isNullOrBlank()) " -Pauth-server.client-secret=..." else "") +
                    " or set env variable(s):" +
                    (if (dockerRegistry.isNullOrBlank()) " DOCKER_REGISTRY" else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "") +
                    (if (authServerUrl.isNullOrBlank()) " AUTH_SERVER_URL" else "") +
                    (if (authServerRealm.isNullOrBlank()) " AUTH_SERVER_REALM" else "") +
                    (if (authServerClientId.isNullOrBlank()) " AUTH_SERVER_CLIENT_ID" else "") +
                    (if (authServerClientSecret.isNullOrBlank()) " AUTH_SERVER_CLIENT_SECRET" else "")
        )
    }
}

sourceSets {
    create("ft") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val ftImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

ftImplementation.isCanBeResolved = true

configurations["ftRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val ft by tasks.creating(Test::class) {
    doFirst {
        if (dmsServiceUser.isNullOrBlank() || dmsServicePassword.isNullOrBlank()) {
            throw IllegalArgumentException(
                "Start gradle build with" +
                        (if (dmsServiceUser.isNullOrBlank()) " -Pdms-service.user=..." else "") +
                        (if (dmsServicePassword.isNullOrBlank()) " -Pdms-service.password=..." else "") +
                        " or set env variable(s):" +
                        (if (dmsServiceUser.isNullOrBlank()) " DMS_SERVICE_USER" else "") +
                        (if (dmsServicePassword.isNullOrBlank()) " DMS_SERVICE_PASSWORD" else "")
            )
        }
    }
    environment["DMS_FT_RESOURCES_PATH"] = "src/ft/resources"
    systemProperties.putAll(mapOf(
            "dms-service.version" to project.version,
            "dms-service.user" to dmsServiceUser,
            "dms-service.password" to dmsServicePassword
    ))
    group = "verification"
    description = "Runs the functional tests"
    testClassesDirs = sourceSets["ft"].output.classesDirs
    classpath = sourceSets["ft"].runtimeClasspath
}

tasks.named("composeUp") {
    dependsOn(":maven-dms-plugin:publishToMavenLocal", ":gradle-dms-client:publishToMavenLocal", ":gradle-dms-plugin:publishToMavenLocal", ":dms-service:dockerBuildImage")
}

dockerCompose.isRequiredBy(ft)

tasks.named("importArtifactoryDump") {
    dependsOn("composeUp")
}

tasks.named("configureMockServer") {
    dependsOn("composeUp")
}

tasks.named("ft") {
    dependsOn("importArtifactoryDump", "configureMockServer")
}

idea.module {
    scopes["PROVIDED"]?.get("plus")?.add(configurations["ftImplementation"])
}

dependencies {
    ftImplementation(project(":common"))
    ftImplementation(project(":test-common"))
    ftImplementation(project(":client"))
    ftImplementation(gradleTestKit())
    ftImplementation("org.junit.jupiter:junit-jupiter-engine")
    ftImplementation("org.junit.jupiter:junit-jupiter-params")
}
