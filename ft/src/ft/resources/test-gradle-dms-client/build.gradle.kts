buildscript {
    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases-local/")
        mavenLocal()
    }
    dependencies {
        classpath("org.octopusden.octopus.dms:gradle-dms-client:${project.properties["dms-service.version"]}")
    }
}

import java.io.File
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.ExportArtifactsTask

tasks {
    val exportArtifactsTask by registering(ExportArtifactsTask::class) {
        this.dmsUrl = project.properties["dms-service.url"] as String
        this.dmsUser = project.properties["dms-service.user"] as String
        this.dmsPassword = project.properties["dms-service.password"] as String
        this.cregUrl = project.properties["creg-service.url"] as String
        this.component = project.properties["component.name"] as String
        this.version = project.properties["component.version"] as String
        this.type = ArtifactType.REPORT
        this.targetDir = File(project.properties["target-dir"] as String)
        this.downloadPrevious = true
    }
}