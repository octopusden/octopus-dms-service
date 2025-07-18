import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.octopusden.octopus.task.ImportArtifactoryDump
import org.octopusden.octopus.task.ConfigureMockServer
import java.time.Duration

plugins {
    java
    idea
    id("org.jetbrains.kotlin.jvm") apply (false)
    signing
    id("io.github.gradle-nexus.publish-plugin")
}

allprojects {
    group = "org.octopusden.octopus.dms"
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

    repositories {
        mavenCentral()
        maven {
            url = uri("https://repo.gradle.org/gradle/libs-releases")
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
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

    dependencies {
        implementation("org.slf4j:slf4j-api") {
            version {
                strictly("1.7.36")
            }
        }
        implementation("ch.qos.logback:logback-classic") {
            version {
                strictly("1.2.11")
            }
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.properties["spring-boot.version"]}"))
        implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${project.properties["spring-cloud.version"]}"))
        implementation(platform("com.fasterxml.jackson:jackson-bom:${project.properties["jackson.version"]}"))
        implementation(platform("org.junit:junit-bom:${project.properties["junit.version"]}"))
        testImplementation(platform("org.junit:junit-bom:${project.properties["junit.version"]}"))
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            suppressWarnings = true
            jvmTarget = "1.8"
        }
    }

    @Suppress("UNUSED_VARIABLE")
    tasks {
        val importArtifactoryDump by registering(ImportArtifactoryDump::class) {
            this.retryLimit = 3
        }
        val configureMockServer by registering(ConfigureMockServer::class)
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
        }
        set("validateFun", { properties: List<String> ->
            val emptyProperties = properties.filter { (project.ext[it] as? String).isNullOrBlank() }
            if (emptyProperties.isNotEmpty()) {
                throw IllegalArgumentException(
                    "Start gradle build with" +
                            (if (emptyProperties.contains("dockerRegistry")) " -Pdocker.registry=..." else "") +
                            (if (emptyProperties.contains("octopusGithubDockerRegistry")) " -Poctopus.github.docker.registry=..." else "") +
                            (if (emptyProperties.contains("authServerUrl")) " -Pauth-server.url=..." else "") +
                            (if (emptyProperties.contains("authServerRealm")) " -Pauth-server.realm=..." else "") +
                            (if (emptyProperties.contains("authServerClientId")) " -Pauth-server.client-id=..." else "") +
                            (if (emptyProperties.contains("authServerClientSecret")) " -Pauth-server.client-secret=..." else "") +
                            (if (emptyProperties.contains("dmsServiceUser")) " -Pdms-service.user=..." else "") +
                            (if (emptyProperties.contains("dmsServicePassword")) " -Pdms-service.password=..." else "") +
                            " or set env variable(s):" +
                            (if (emptyProperties.contains("dockerRegistry")) " DOCKER_REGISTRY" else "") +
                            (if (emptyProperties.contains("octopusGithubDockerRegistry")) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "") +
                            (if (emptyProperties.contains("authServerUrl")) " AUTH_SERVER_URL" else "") +
                            (if (emptyProperties.contains("authServerRealm")) " AUTH_SERVER_REALM" else "") +
                            (if (emptyProperties.contains("authServerClientId")) " AUTH_SERVER_CLIENT_ID" else "") +
                            (if (emptyProperties.contains("authServerClientSecret")) " AUTH_SERVER_CLIENT_SECRET" else "") +
                            (if (emptyProperties.contains("dmsServiceUser")) " DMS_SERVICE_USER" else "") +
                            (if (emptyProperties.contains("dmsServicePassword")) " DMS_SERVICE_PASSWORD" else "")
                )
            }
        })
    }
}
