import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.InetAddress
import java.time.Duration
import java.util.zip.CRC32
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    idea
    id("org.jetbrains.kotlin.jvm")
    signing
    id("io.github.gradle-nexus.publish-plugin")
    // Kotlin static-analysis tools — declared at root (apply false), applied per Kotlin subproject below.
    id("io.gitlab.arturbosch.detekt") apply false
    id("org.jlleitschuh.gradle.ktlint") apply false
    // Octopus quality-gates convention plugin — configures detekt/ktlint and wires qualityStatic.
    id("org.octopusden.octopus-quality")
}

octopusQuality {
    // Repo has no coverage target wired for the gate — disable coverage verification.
    coverage {
        enabled.set(false)
    }
    // Enforce the gate: detekt/ktlint violations fail the build. Current debt is absorbed by
    // the committed detekt-baseline.xml / ktlint-baseline.xml files.
    kotlin {
        failOnViolation.set(true)
    }
    // Functional-test module is not part of the static quality gate.
    // The dms-service ':test' task is an OKD/OpenShift integration suite (it wires the oc-template
    // tasks ocProcess/ocCreate and requires a live cluster + `oc` CLI); it cannot run on the CI
    // runner, so it is excluded from the coverage aggregate together with the functional tests.
    excludeTasks("ft", ":ft:ft", ":dms-service:test")
}

val defaultVersion = "${
    with(CRC32()) {
        update(InetAddress.getLocalHost().hostName.toByteArray())
        value
    }
}-SNAPSHOT"

allprojects {
    group = "org.octopusden.octopus.dms"
    if (version == "unspecified") {
        version = defaultVersion
    }
}

// Publications that are meant to reach Maven Central, as
// "<project path>|<publication name>|<groupId>:<artifactId>".
//
// Project PATHS, not names: the client-side modules live under client/ while their project
// paths are flat (`:client` is client/client), and only a path can express the root project
// (':'), so a name-based check would silently ignore a publication added there.
// The coordinate as well as the path, because a project that already publishes can grow a
// second publication or change its artifactId without changing the set of publishing
// projects — both put a new coordinate on Central. `:gradle-dms-plugin` legitimately has two
// (its own, plus the plugin marker the `java-gradle-plugin` plugin adds under a different
// groupId), which is exactly the shape a project-level check cannot express.
// An allowlist rather than a denylist, so anything newly added defaults to unpublished.
// This guards the set of COORDINATES; what each publication attaches is guarded at release
// time by the shared workflow, which rejects a fat jar, an `-all` artifact, a Spring Boot jar
// or anything over max-central-artifact-mb before it reaches Central.
val centralPublishedPublications = setOf(
    ":client|maven|org.octopusden.octopus.dms:client",
    ":common|maven|org.octopusden.octopus.dms:common",
    ":gradle-dms-client|maven|org.octopusden.octopus.dms:gradle-dms-client",
    ":gradle-dms-plugin|pluginMaven|org.octopusden.octopus.dms:gradle-dms-plugin",
    ":gradle-dms-plugin|gradle-dms-pluginPluginMarkerMaven|" +
        "org.octopusden.octopus-dms:org.octopusden.octopus-dms.gradle.plugin",
    ":maven-dms-plugin|maven|org.octopusden.octopus.dms:maven-dms-plugin",
    ":metarunners|maven|org.octopusden.octopus.dms:metarunners",
)

fun centralPublicationPolicyProblems(): List<String> {
    // allprojects, not subprojects: the root is a publishable project like any other.
    // A project without maven-publish has no publishing extension at all, hence findByType.
    val actual = allprojects
        .filter { it.plugins.hasPlugin("maven-publish") }
        .flatMap { candidate ->
            candidate.extensions
                .findByType(PublishingExtension::class.java)
                ?.publications
                ?.withType(MavenPublication::class.java)
                ?.map { "${candidate.path}|${it.name}|${it.groupId}:${it.artifactId}" }
                ?: emptyList()
        }.toSet()
    return if (actual == centralPublishedPublications) {
        emptyList()
    } else {
        listOf(
            "Maven Central publication set drifted.\n" +
                "  allowlisted: ${centralPublishedPublications.sorted()}\n" +
                "  publishing:  ${actual.sorted()}",
        )
    }
}

// A policy problem must fail its own gate, not configuration: a throw from
// `gradle.projectsEvaluated` would break every invocation the moment the set drifts —
// `dependencies`, `tasks`, IDE sync — instead of only the paths that publish.
val verifyCentralPublicationPolicy = tasks.register("verifyCentralPublicationPolicy") {
    group = "verification"
    description = "Fails if the publications reaching Maven Central drift from the allowlist."
    doLast {
        val problems = centralPublicationPolicyProblems()
        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString("\n\n"))
        }
    }
}

gradle.projectsEvaluated {
    allprojects.forEach { proj ->
        // The upload and install tasks themselves, so the gate is a dependency OF the leaf
        // rather than a sibling of it: invoking `publishAllPublicationsToSonatypeRepository`
        // (or a single publish*PublicationToSonatypeRepository) directly cannot skip it, and
        // parallel execution cannot start an upload while the gate is still running.
        proj.tasks
            .withType(AbstractPublishToMaven::class.java)
            .configureEach { dependsOn(verifyCentralPublicationPolicy) }
        // Plus the aggregate entry points, which own no artifact themselves. The staging
        // transitions are included because a resumed release can invoke them on their own.
        // publishToSonatype and the staging tasks only exist with -Pnexus and `publish` is
        // per-project, so match by name rather than forcing any of them into existence.
        proj.tasks
            .matching {
                it.name in setOf(
                    "publish",
                    "publishToMavenLocal",
                    "publishToSonatype",
                    "closeSonatypeStagingRepository",
                    "releaseSonatypeStagingRepository",
                    "closeAndReleaseSonatypeStagingRepository",
                )
            }.configureEach { dependsOn(verifyCentralPublicationPolicy) }
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && (requested.name == "kotlin-stdlib-jdk7" || requested.name == "kotlin-stdlib-jdk8")) {
            useTarget("org.jetbrains.kotlin:kotlin-stdlib:${project.properties["kotlin.version"]}")
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
        }
    }
    transitionCheckOptions {
        maxRetries.set(60)
        delayBetween.set(Duration.ofSeconds(30))
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "idea")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "signing")
    // Kotlin static analysis — applied per subproject so the convention plugin's reactive
    // configuration wires detekt/ktlintCheck tasks (avoids a hollow quality gate).
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    repositories {
        mavenCentral()
        maven {
            url = uri("https://repo.gradle.org/gradle/libs-releases")
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
        JavaVersion.VERSION_21.let {
            sourceCompatibility = it
            targetCompatibility = it
        }
    }

    kotlin {
        compilerOptions.jvmTarget = JvmTarget.JVM_21
    }

    idea.module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging{
            info.events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
        }
    }

    ext {
        System.getenv().let {
            set("signingRequired", it.containsKey("ORG_GRADLE_PROJECT_signingKey") && it.containsKey("ORG_GRADLE_PROJECT_signingPassword"))
            set("dockerRegistry", System.getenv().getOrDefault("DOCKER_REGISTRY", project.properties["docker.registry"]))
            set("octopusGithubDockerRegistry", System.getenv().getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]))
            set("authServerUrl", System.getenv().getOrDefault("AUTH_SERVER_URL", project.properties["auth-server.url"]))
            set("authServerRealm", System.getenv().getOrDefault("AUTH_SERVER_REALM", project.properties["auth-server.realm"]))
            set("authServerClientId", System.getenv().getOrDefault("AUTH_SERVER_CLIENT_ID", project.properties["auth-server.client-id"]))
            set("authServerClientSecret", System.getenv().getOrDefault("AUTH_SERVER_CLIENT_SECRET", project.properties["auth-server.client-secret"]))
            set("dmsServiceUser", System.getenv().getOrDefault("DMS_SERVICE_USER", project.properties["dms-service.user"]))
            set("dmsServicePassword", System.getenv().getOrDefault("DMS_SERVICE_PASSWORD", project.properties["dms-service.password"]))
            set("testPlatform", it.getOrDefault("TEST_PLATFORM", properties["test.platform"]))
            set("okdActiveDeadlineSeconds", it.getOrDefault("OKD_ACTIVE_DEADLINE_SECONDS", properties["okd.active-deadline-seconds"]))
            set("okdProject", it.getOrDefault("OKD_PROJECT", properties["okd.project"]))
            set("okdClusterDomain", it.getOrDefault("OKD_CLUSTER_DOMAIN", properties["okd.cluster-domain"]))
            set("okdWebConsoleUrl", (it.getOrDefault("OKD_WEB_CONSOLE_URL", properties["okd.web-console-url"]) as String).trimEnd('/'))
        }
        val supportedTestPlatforms = listOf("okd")
        val testPlatform = project.ext["testPlatform"] as String
        if (testPlatform.isNotBlank() && testPlatform !in supportedTestPlatforms) {
            throw IllegalArgumentException("Test platform must be set to one of the following $supportedTestPlatforms. Start gradle build with -Ptest.platform=... or set env variable TEST_PLATFORM")
        }
        val mandatoryProperties = mutableListOf("dockerRegistry", "octopusGithubDockerRegistry")
        if (project.ext["testPlatform"] == "okd") {
            mandatoryProperties.add("okdActiveDeadlineSeconds")
            mandatoryProperties.add("okdProject")
            mandatoryProperties.add("okdClusterDomain")
        }
        val undefinedProperties = mandatoryProperties.filter { (project.ext[it] as String).isBlank() }
        if (undefinedProperties.isNotEmpty()) {
            throw IllegalArgumentException(
                "Start gradle build with" +
                        (if (undefinedProperties.contains("dockerRegistry")) " -Pdocker.registry=..." else "") +
                        (if (undefinedProperties.contains("octopusGithubDockerRegistry")) " -Poctopus.github.docker.registry=..." else "") +
                        (if (undefinedProperties.contains("okdActiveDeadlineSeconds")) " -Pokd.active-deadline-seconds=..." else "") +
                        (if (undefinedProperties.contains("okdProject")) " -Pokd.project=..." else "") +
                        (if (undefinedProperties.contains("okdClusterDomain")) " -Pokd.cluster-domain=..." else "") +
                        " or set env variable(s):" +
                        (if (undefinedProperties.contains("dockerRegistry")) " DOCKER_REGISTRY" else "") +
                        (if (undefinedProperties.contains("octopusGithubDockerRegistry")) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "") +
                        (if (undefinedProperties.contains("okdActiveDeadlineSeconds")) " OKD_ACTIVE_DEADLINE_SECONDS" else "") +
                        (if (undefinedProperties.contains("okdProject")) " OKD_PROJECT" else "") +
                        (if (undefinedProperties.contains("okdClusterDomain")) " OKD_CLUSTER_DOMAIN" else "")
            )
        }
    }
}
