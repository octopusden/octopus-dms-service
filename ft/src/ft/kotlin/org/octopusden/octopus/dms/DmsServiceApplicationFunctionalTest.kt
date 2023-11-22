package org.octopusden.octopus.dms

import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactDTO
import org.octopusden.octopus.dms.client.impl.ClassicDmsServiceClient
import org.octopusden.octopus.dms.client.impl.DmsServiceClientParametersProvider
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit.MINUTES
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DmsServiceApplicationFunctionalTest : DmsServiceApplicationBaseTest() {
    private val mvn = with(System.getenv()["M2_HOME"] ?: System.getenv()["MAVEN_HOME"]) {
        "${this?.let { "$it/bin/" } ?: ""}mvn"
    }
    private val cregServiceUrl = "http://localhost:4567"
    private val dmsServiceUrl = "http://localhost:8765/dms-service"

    override val client = ClassicDmsServiceClient(
        object : DmsServiceClientParametersProvider {
            override fun getApiUrl() = dmsServiceUrl
            override fun getBearerToken() = null
            override fun getBasicCredentials() =
                "${System.getProperty("dms-service.user")}:${System.getProperty("dms-service.password")}"
        }
    )

    @Test
    fun testGradleDmsClient() {
        val reports = listOf(
            "REPORT0354" to MavenArtifactCoordinatesDTO(
                GavDTO(
                    "test.gradle.dms.client",
                    "report",
                    eeComponentReleaseVersion0354.releaseVersion,
                    "txt"
                )
            ),
            "REPORT0353" to MavenArtifactCoordinatesDTO(
                GavDTO(
                    "test.gradle.dms.client",
                    "report",
                    eeComponentReleaseVersion0353.releaseVersion,
                    "txt"
                )
            )
        )
        reports.forEach { report ->
            report.first.byteInputStream(UTF_8).use {
                client.registerComponentVersionArtifact(
                    eeComponent,
                    report.second.gav.version,
                    client.uploadArtifact(report.second, it, "report").id,
                    RegisterArtifactDTO(ArtifactType.REPORT)
                )
            }
        }
        val buildDir = File("").resolve("build")
        val projectDir = buildDir.resolve("resources").resolve("ft").resolve("test-gradle-dms-client")
        val targetDir = projectDir.resolve("export")
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pdms-service.version=${System.getProperty("dms-service.version")}",
                "-Pdms-service.url=$dmsServiceUrl",
                "-Pdms-service.user=${System.getProperty("dms-service.user")}",
                "-Pdms-service.password=${System.getProperty("dms-service.password")}",
                "-Pcreg-service.url=$cregServiceUrl",
                "-Pcomponent.name=$eeComponent",
                "-Pcomponent.version=${eeComponentReleaseVersion0354.releaseVersion}",
                "-Ptarget-dir=${targetDir.toPath().toAbsolutePath()}",
                "exportArtifactsTask",
                "--info"
            ).build()
        with(buildDir.resolve("logs").resolve("test-gradle-dms-client.log")) {
            this.parentFile.mkdirs()
            this.outputStream().use {
                it.writer(UTF_8).write(result.output)
            }
        }
        reports.forEach {
            it.first.byteInputStream(UTF_8).use { expected ->
                targetDir.resolve(it.second.gav.toPath().substringAfterLast('/')).inputStream().use { actual ->
                    assertArrayEquals(expected.readBytes(), actual.readBytes())
                }
            }
        }
    }

    @Test
    fun testGradleDmsPlugin() {
        val releaseNotesRELEASE = getResource(releaseReleaseNotesFileName)
        releaseNotesRELEASE.openStream().use {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                client.uploadArtifact(releaseNotesCoordinates, it, releaseReleaseNotesFileName).id,
                RegisterArtifactDTO(ArtifactType.NOTES)
            )
        }
        val buildDir = File("").resolve("build")
        val projectDir = buildDir.resolve("resources").resolve("ft").resolve("test-gradle-dms-plugin")
        val targetDir = projectDir.resolve("export")
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "-Pdms-service.version=${System.getProperty("dms-service.version")}",
                "-Pdms-service.url=$dmsServiceUrl",
                "-Pdms-service.user=${System.getProperty("dms-service.user")}",
                "-Pdms-service.password=${System.getProperty("dms-service.password")}",
                "-Pcomponent.name=$eeComponent",
                "-Pcomponent.version=${eeComponentReleaseVersion0354.buildVersion}",
                "-Partifact.name=${releaseNotesCoordinates.gav.artifactId}",
                "-Partifact.version=${releaseNotesCoordinates.gav.version}",
                "-Partifact.classifier=${releaseNotesCoordinates.gav.classifier}",
                "-Ptarget-dir=${targetDir.toPath().toAbsolutePath()}",
                "downloadReleaseNotes",
                "--info"
            ).build()
        with(buildDir.resolve("logs").resolve("test-gradle-dms-plugin.log")) {
            this.parentFile.mkdirs()
            this.outputStream().use {
                it.writer(UTF_8).write(result.output)
            }
        }
        releaseNotesRELEASE.openStream().use { expected ->
            targetDir.resolve(releaseNotesCoordinates.gav.toPath().substringAfterLast("/")).inputStream().use { actual ->
                assertArrayEquals(expected.readBytes(), actual.readBytes())
            }
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsDifferentRepos() {
        with(runMavenDmsPlugin("different-repos.log", "validate-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=${devMavenDistributionCoordinates.toString().replace(":1.0:", ":")},${releaseMavenDistributionCoordinates.toString().replace(":1.0:", ":")}",
            "-Dartifacts.coordinates.version=1.0",
            "-Dartifacts.coordinates.deb=$devDebianDistributionCoordinates,$releaseDebianDistributionCoordinates",
            "-Dartifacts.coordinates.rpm=$devRpmDistributionCoordinates,$releaseRpmDistributionCoordinates"
        ))) {
            assertEquals(1, this.first)
            assertTrue(this.second.contains("[ERROR] Artifact '$devMavenDistributionCoordinates' is invalidated."))
            assertTrue(this.second.contains("${devMavenDistributionCoordinates.gav.toPath().substringAfterLast('/')}: third party license file does not found"))
            assertTrue(this.second.contains("[ERROR] Artifact '$releaseMavenDistributionCoordinates' is invalidated."))
            assertTrue(this.second.contains("${releaseMavenDistributionCoordinates.gav.toPath().substringAfterLast('/')}: third party license file does not found"))
            assertTrue(this.second.contains("[INFO] Validated artifact '$devDebianDistributionCoordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
            assertTrue(this.second.contains("[INFO] Validated artifact '$releaseDebianDistributionCoordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
            assertTrue(this.second.contains("[INFO] Validated artifact '$devRpmDistributionCoordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
            assertTrue(this.second.contains("[INFO] Validated artifact '$releaseRpmDistributionCoordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsInvalidDistribution() {
        with(runMavenDmsPlugin("invalid-distribution.log", "validate-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=file:///${File("").absolutePath}/\${env.DMS_FT_RESOURCES_PATH}/test-maven-dms-plugin/\${component}-0\${major}.\${minor}.\${service}.\${fix}-\${build}.zip?artifactId=distribution"
        ))) {
            assertEquals(1, this.first)
            assertTrue(this.second.contains("[ERROR] Artifact 'corp.domain.dms.ee-component.distribution:distribution:03.54.30.64-1:zip' is invalidated."))
            assertTrue(this.second.contains("distribution-${eeComponentReleaseVersion0354.buildVersion}.zip/lib/forbidden.jar/forbidden.xml: line 1, token '<providerName>unallowed</providerName>' matches regexp '.*unallowed.*'"))
            assertTrue(this.second.contains("distribution-${eeComponentReleaseVersion0354.buildVersion}.zip/forbidden.xml: line 1, token '<providerName>unallowed</providerName>' matches regexp '.*unallowed.*'"))
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsExcludeFile() {
        with(runMavenDmsPlugin("exclude-file.log", "validate-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=file:///${File("").absolutePath}/\${env.DMS_FT_RESOURCES_PATH}/test-maven-dms-plugin/\${component}-\${version}.zip?artifactId=distribution&classifier=test",
            "-DexcludeFiles=forbidden.xml"
        ))) {
            assertEquals(0, this.first)
            assertTrue(this.second.contains("[INFO] Validated artifact 'corp.domain.dms.$eeComponent.distribution:distribution:${eeComponentReleaseVersion0354.buildVersion}:zip:test' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsWlIgnore() {
        with(runMavenDmsPlugin("wl-ignore.log", "validate-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=file:///${File("").absolutePath}/\${env.DMS_FT_RESOURCES_PATH}/test-maven-dms-plugin/\${component}-\${version}.zip?artifactId=distribution&classifier=test",
            "-DwlIgnore=${File("").absolutePath}/src/ft/resources/test-maven-dms-plugin/.wlignore.json"
        ))) {
            assertEquals(0, this.first)
            assertTrue(this.second.contains("[INFO] Validated artifact 'corp.domain.dms.$eeComponent.distribution:distribution:${eeComponentReleaseVersion0354.buildVersion}:zip:test' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
        }
    }

    @Test
    fun testMavenDmsPluginUploadArtifactsDifferentRepos() {
        with(runMavenDmsPlugin("different-repos.log", "upload-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=${releaseMavenDistributionCoordinates.toString().replace(":1.0:", ":")}",
            "-Dartifacts.coordinates.version=1.0",
            "-Dartifacts.coordinates.deb=$releaseDebianDistributionCoordinates",
            "-Dartifacts.coordinates.rpm=$releaseRpmDistributionCoordinates"
        ))) {
            assertEquals(0, this.first)
            assertTrue(this.second.contains("[INFO] Uploaded distribution artifact '$releaseMavenDistributionCoordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
            assertTrue(this.second.contains("[INFO] Uploaded distribution artifact '$releaseDebianDistributionCoordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
            assertTrue(this.second.contains("[INFO] Uploaded distribution artifact '$releaseRpmDistributionCoordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
        }
        assertEquals(releaseMavenDistributionCoordinates.gav, (client.findArtifact(releaseMavenDistributionCoordinates) as MavenArtifactDTO).gav)
        assertEquals(releaseDebianDistributionCoordinates.deb, (client.findArtifact(releaseDebianDistributionCoordinates) as DebianArtifactDTO).deb)
        assertEquals(releaseRpmDistributionCoordinates.rpm, (client.findArtifact(releaseRpmDistributionCoordinates) as RpmArtifactDTO).rpm)
    }

    @Test
    fun testMavenDmsPluginUploadArtifactsFiles() {
        val resourcesDir = File("").resolve("src").resolve("ft").resolve("resources").resolve("test-maven-dms-plugin").absoluteFile
        val distribution1 = resourcesDir.resolve(".wlignore.json")
        val distribution1Coordinates = MavenArtifactCoordinatesDTO(GavDTO(
            "corp.domain.dms.$eeComponent.distribution",
            "distribution1",
            eeComponentReleaseVersion0354.buildVersion,
            "json"
        ))
        val distribution2 = resourcesDir.resolve("ee-component-03.54.30.64-1.zip")
        val distribution2Coordinates = MavenArtifactCoordinatesDTO(GavDTO(
            "corp.domain.dms.$eeComponent.distribution",
            "distribution2",
            eeComponentReleaseVersion0354.buildVersion,
            "zip"
        ))
        with(runMavenDmsPlugin("files.log", "upload-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=${distribution1.toPath().toUri()}?artifactId=distribution1,${distribution2.toPath().toUri()}?artifactId=distribution2"
        ))) {
            assertEquals(0, this.first)
            assertTrue(this.second.contains("[INFO] Uploaded distribution artifact '$distribution1Coordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
            assertTrue(this.second.contains("[INFO] Uploaded distribution artifact '$distribution2Coordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
        }
        client.downloadComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            client.findArtifact(distribution1Coordinates).id
        ).use { response ->
            distribution1.inputStream().use {
                assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
            }
        }
        client.downloadComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            client.findArtifact(distribution2Coordinates).id
        ).use { response ->
            distribution2.inputStream().use {
                assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
            }
        }
    }

    @Test
    fun testMavenDmsPluginUpload() {
        val file = File("").resolve("src").resolve("ft").resolve("resources").resolve("test-maven-dms-plugin").resolve("ee-component-03.54.30.64-1.zip")
        val coordinates = MavenArtifactCoordinatesDTO(GavDTO(
            "corp.domain.dms.$eeComponent.distribution",
            "test",
            eeComponentReleaseVersion0354.buildVersion,
            "zip"
        ))
        with(runMavenDmsPlugin("file.log", "upload", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dname=test",
            "-Dfile=${file.absolutePath}"
        ))) {
            assertEquals(0, this.first)
            assertTrue(this.second.contains("[INFO] Uploaded distribution artifact '$coordinates' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'"))
        }
        client.downloadComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            client.findArtifact(coordinates).id
        ).use { response ->
            file.inputStream().use {
                assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
            }
        }
    }

    private fun runMavenDmsPlugin(
        outputFileName: String,
        goal: String,
        parameters: List<String>
    ): Pair<Int, List<String>> {
        val outputFile = File("").resolve("build").resolve("logs")
            .resolve("test-maven-dms-plugin-$goal").resolve(outputFileName)
            .also { it.parentFile.mkdirs() }
        val process = ProcessBuilder(listOf(mvn,
            "org.octopusden.octopus.dms:maven-dms-plugin:${System.getProperty("dms-service.version")}:$goal",
            "-e",
            "-Ddms.url=$dmsServiceUrl",
            "-Ddms.username=${System.getProperty("dms-service.user")}",
            "-Ddms.password=${System.getProperty("dms-service.password")}",
            "-Dtype=distribution"
        ) + parameters)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .start()
        process.waitFor(5, MINUTES)
        return process.exitValue() to outputFile.readLines(UTF_8)
    }
}