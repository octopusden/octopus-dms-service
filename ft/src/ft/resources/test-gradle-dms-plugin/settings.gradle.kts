pluginManagement {
    plugins {
        id("org.octopusden.octopus-dms") version (extra["dms-service.version"] as String)
    }
    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases-local/")
        mavenLocal()
    }
}