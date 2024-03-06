plugins {
    id("com.avast.gradle.docker-compose") version "0.14.3"
    id("java-gradle-plugin")
}

@Suppress("UNCHECKED_CAST")
val extValidateFun = project.ext["validateFun"] as ((List<String>) -> Unit)
fun String.getExt() = project.ext[this] as? String

dockerCompose {
    useComposeFiles.add("${projectDir}/src/ft/docker/docker-compose.yaml")
    waitForTcpPorts = true
    captureContainersOutputToFiles = File("$buildDir${File.separator}docker_logs")
    environment.putAll(
        mapOf(
            "DMS_SERVICE_VERSION" to project.version,
            "OCTOPUS_COMPONENTS_REGISTRY_SERVICE_VERSION" to project.properties["octopus-components-registry-service.version"],
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "AUTH_SERVER_URL" to "authServerUrl".getExt(),
            "AUTH_SERVER_REALM" to "authServerRealm".getExt(),
            "AUTH_SERVER_CLIENT_ID" to "authServerClientId".getExt(),
            "AUTH_SERVER_CLIENT_SECRET" to "authServerClientSecret".getExt()
        )
    )
}

tasks.getByName("composeUp").doFirst {
    extValidateFun.invoke(listOf(
            "dockerRegistry", "octopusGithubDockerRegistry",
            "authServerUrl", "authServerRealm", "authServerClientId", "authServerClientSecret"
    ))
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
        extValidateFun.invoke(listOf("dmsServiceUser", "dmsServicePassword"))
    }
    environment["DMS_FT_RESOURCES_PATH"] = "src/ft/resources"
    systemProperties.putAll(mapOf(
            "dms-service.version" to project.version,
            "dms-service.user" to "dmsServiceUser".getExt(),
            "dms-service.password" to "dmsServicePassword".getExt()
    ))
    group = "verification"
    description = "Runs the functional tests"
    testClassesDirs = sourceSets["ft"].output.classesDirs
    classpath = sourceSets["ft"].runtimeClasspath
}

tasks.named("composeUp") {
    dependsOn(
        ":maven-dms-plugin:publishToMavenLocal", ":gradle-dms-client:publishToMavenLocal",
        ":gradle-dms-plugin:publishToMavenLocal", ":dms-service:dockerBuildImage"
    )
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
    ftImplementation(gradleTestKit())
    ftImplementation("org.octopusden.octopus.dms:client:${project.version}")
    ftImplementation("org.junit.jupiter:junit-jupiter-engine")
    ftImplementation("org.junit.jupiter:junit-jupiter-params")
}
