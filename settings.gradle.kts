pluginManagement {
    plugins {
        val kotlinVersion = extra["kotlin.version"] as String
        val springBootVersion = extra["spring-boot.version"] as String

        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.jpa") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.noarg") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
        id("io.github.gradle-nexus.publish-plugin") version("1.1.0") apply(false)
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "octopus-dms-service"

include(":common")

include(":test-common")

include(":dms-service")
project(":dms-service").projectDir = File("$rootDir/server")

include(":client")
project(":client").projectDir = File("$rootDir/client/client")

include(":maven-dms-plugin")
project(":maven-dms-plugin").projectDir = File("$rootDir/client/maven-dms-plugin")

include(":gradle-dms-client")
project(":gradle-dms-client").projectDir = File("$rootDir/client/gradle-dms-client")

include(":gradle-dms-plugin")
project(":gradle-dms-plugin").projectDir = File("$rootDir/client/gradle-dms-plugin")

include(":ft")

include(":metarunners")


