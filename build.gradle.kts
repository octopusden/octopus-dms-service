import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.octopusden.octopus.task.ImportArtifactoryDump
import org.octopusden.octopus.task.ConfigureMockServer

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
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
        }
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
}
