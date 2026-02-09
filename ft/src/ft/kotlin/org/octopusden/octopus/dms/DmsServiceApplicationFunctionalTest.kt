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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactDTO
import java.util.stream.Stream

class DmsServiceApplicationFunctionalTest : DmsServiceApplicationBaseTest() {
    private val isWindowsSystem by lazy {
        System.getProperty("os.name").lowercase().contains("win")
    }

    private val mvn = with(System.getenv()["M2_HOME"] ?: System.getenv()["MAVEN_HOME"]) {
        val mavenCommand = if (isWindowsSystem) mvnWinCommand else mvnCommonCommand
        "${this?.let { "$it/bin/" } ?: ""}$mavenCommand"
    }

    private val cregHost = System.getProperty("test.components-registry-host")
        ?: throw Exception("System property 'test.components-registry-host' must be defined")
    private val apiGatewayHost = System.getProperty("test.api-gateway-host")
        ?: throw Exception("System property 'test.api-gateway-host' must be defined")
    private val cregServiceUrl = "http://$cregHost"
    private val dmsServiceUrl = "http://$apiGatewayHost/dms-service"

    override val client = ClassicDmsServiceClient(
        object : DmsServiceClientParametersProvider {
            override fun getApiUrl() = dmsServiceUrl
            override fun getBearerToken() = null
            override fun getBasicCredentials() =
                "${System.getProperty("dms-service.user")}:${System.getProperty("dms-service.password")}"
        }
    )

    @ParameterizedTest
    @MethodSource("gradleVersions")
    fun testGradleDmsClient(gradleVersion: String) {
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
        val targetDir = projectDir.resolve("export-$gradleVersion")
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
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
        with(buildDir.resolve("logs").resolve("test-gradle-dms-client-$gradleVersion.log")) {
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
            "-Dartifacts.coordinates=${DEV_ARTIFACTS_COORDINATES},${RELEASE_ARTIFACTS_COORDINATES}",
            "-Dartifacts.coordinates.version=1.0",
            "-Dartifacts.coordinates.deb=$DEV_DEB_ARTIFACTS_COORDINATES,$RELEASE_DEB_ARTIFACTS_COORDINATES",
            "-Dartifacts.coordinates.rpm=$DEV_RPM_ARTIFACTS_COORDINATES,$RELEASE_RPM_ARTIFACTS_COORDINATES",
            "-Dtype=distribution"
        ))) {
            assertEquals(1, this.first)
            assertContains(this.second, "[ERROR] Artifact '${devMavenDistributionCoordinates.toPath()}' validation errors:")
            assertContains(this.second, "${devMavenDistributionCoordinates.gav.toPath().substringAfterLast('/')}: third party license file does not found")
            assertContains(this.second, "[ERROR] Artifact '${releaseMavenDistributionCoordinates.toPath()}' validation errors:")
            assertContains(this.second, "${releaseMavenDistributionCoordinates.gav.toPath().substringAfterLast('/')}: third party license file does not found")
            assertContains(this.second, "[INFO] Validated artifact '${devDebianDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
            assertContains(this.second, "[INFO] Validated artifact '${releaseDebianDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
            assertContains(this.second, "[INFO] Validated artifact '${devRpmDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
            assertContains(this.second, "[INFO] Validated artifact '${releaseRpmDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsInvalidDistribution() {
        with(runMavenDmsPlugin("invalid-distribution.log", "validate-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=file:///${File("").absolutePath}/src/ft/resources/test-maven-dms-plugin/$eeComponent-${eeComponentReleaseVersion0354.buildVersion}.zip?artifactId=distribution",
            "-Dtype=distribution"
        ))) {
            assertEquals(1, this.first)
            assertContains(this.second, "[ERROR] Artifact 'corp/domain/dms/$eeComponent/distribution/distribution/${eeComponentReleaseVersion0354.buildVersion}/distribution-${eeComponentReleaseVersion0354.buildVersion}.zip' validation errors:")
            assertContains(this.second, "distribution-${eeComponentReleaseVersion0354.buildVersion}.zip/lib/forbidden.jar/forbidden.xml: line 1, token '<providerName>unallowed</providerName>' matches regexp '.*unallowed.*'")
            assertContains(this.second, "distribution-${eeComponentReleaseVersion0354.buildVersion}.zip/forbidden.xml: line 1, token '<providerName>unallowed</providerName>' matches regexp '.*unallowed.*'")
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsExcludeFile() {
        val coordValue = "file:///${File("").absolutePath}/src/ft/resources/test-maven-dms-plugin/$eeComponent-${eeComponentReleaseVersion0354.buildVersion}.zip?artifactId=distribution&classifier=test"
        val coordArgs = if (isWindowsSystem) "\"$coordValue\"" else coordValue
        with(runMavenDmsPlugin("exclude-file.log", "validate-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=$coordArgs",
            "-DexcludeFiles=forbidden.xml",
            "-Dtype=distribution"
        ))) {
            assertEquals(0, this.first)
            assertContains(this.second, "[INFO] Validated artifact 'corp/domain/dms/$eeComponent/distribution/distribution/${eeComponentReleaseVersion0354.buildVersion}/distribution-${eeComponentReleaseVersion0354.buildVersion}-test.zip' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsWlIgnore() {
        val coordValue = "file:///${File("").absolutePath}/src/ft/resources/test-maven-dms-plugin/$eeComponent-${eeComponentReleaseVersion0354.buildVersion}.zip?artifactId=distribution&classifier=test"
        val coordArgs = if (isWindowsSystem) "\"$coordValue\"" else coordValue
        with(runMavenDmsPlugin("wl-ignore.log", "validate-artifacts", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
            "-Dartifacts.coordinates=$coordArgs",
            "-DwlIgnore=${File("").absolutePath}/src/ft/resources/test-maven-dms-plugin/.wlignore.json",
            "-Dtype=distribution"
        ))) {
            assertEquals(0, this.first)
            assertContains(this.second, "[INFO] Validated artifact 'corp/domain/dms/$eeComponent/distribution/distribution/${eeComponentReleaseVersion0354.buildVersion}/distribution-${eeComponentReleaseVersion0354.buildVersion}-test.zip' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
        }
    }

    @Test
    fun testMavenDmsPluginUploadArtifactsDifferentRepos() {
        with(
            runMavenDmsPlugin(
                "different-repos.log", "upload-artifacts", listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=${RELEASE_ARTIFACTS_COORDINATES}",
                    "-Dartifacts.coordinates.version=1.0",
                    "-Dartifacts.coordinates.deb=$RELEASE_DEB_ARTIFACTS_COORDINATES",
                    "-Dartifacts.coordinates.rpm=$RELEASE_RPM_ARTIFACTS_COORDINATES",
                    "-Dartifacts.coordinates.docker=$RELEASE_DOCKER_ARTIFACTS_COORDINATES",
                    "-Dtype=distribution"
                )
            )
        ) {
            assertEquals(0, this.first, this.second.joinToString("\n"))
            assertContains(this.second, "[INFO] Uploaded distribution artifact '${releaseMavenDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
            assertContains(this.second, "[INFO] Uploaded distribution artifact '${releaseDebianDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
            assertContains(this.second, "[INFO] Uploaded distribution artifact '${releaseRpmDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
            assertContains(this.second, "[INFO] Uploaded distribution artifact '${releaseDockerDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
        }
        assertEquals(
            releaseMavenDistributionCoordinates.gav,
            (client.findArtifact(releaseMavenDistributionCoordinates) as MavenArtifactDTO).gav
        )
        assertEquals(
            releaseDebianDistributionCoordinates.deb,
            (client.findArtifact(releaseDebianDistributionCoordinates) as DebianArtifactDTO).deb
        )
        assertEquals(
            releaseRpmDistributionCoordinates.rpm,
            (client.findArtifact(releaseRpmDistributionCoordinates) as RpmArtifactDTO).rpm
        )
        val dockerArtifact = client.findArtifact(releaseDockerDistributionCoordinates) as DockerArtifactDTO
        assertEquals(releaseDockerDistributionCoordinates.image, dockerArtifact.image)
        assertEquals(releaseDockerDistributionCoordinates.tag, dockerArtifact.tag)
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
            "-Dartifacts.coordinates=${distribution1.toPath().toUri()}?artifactId=distribution1,${distribution2.toPath().toUri()}?artifactId=distribution2",
            "-Dtype=distribution"
        ))) {
            assertEquals(0, this.first)
            assertContains(this.second, "[INFO] Uploaded distribution artifact '${distribution1Coordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
            assertContains(this.second, "[INFO] Uploaded distribution artifact '${distribution2Coordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
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
            "-Dfile=${file.absolutePath}",
            "-Dtype=distribution"
        ))) {
            assertEquals(0, this.first)
            assertContains(this.second, "[INFO] Uploaded distribution artifact '${coordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'")
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

    @Test
    fun testMavenDmsPluginPublish() {
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0353.buildVersion,
            client.addArtifact(releaseMavenDistributionCoordinates).id,
            RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
        )
        with(runMavenDmsPlugin("file.log", "publish", listOf(
            "-Dcomponent=$eeComponent",
            "-Dversion=${eeComponentReleaseVersion0353.buildVersion}"
        ))) {
            assertEquals(0, this.first)
            assertContains(this.second, "[INFO] Published component '$eeComponent' version '${eeComponentReleaseVersion0353.buildVersion}'")
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
            "-Ddms.password=${System.getProperty("dms-service.password")}"
        ) + parameters)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .start()
        process.waitFor(5, MINUTES)
        return process.exitValue() to outputFile.readLines(UTF_8)
    }

    private fun assertContains(source: List<String>, actual: String) {
        assertTrue(source.contains(actual), "Expected the source $source to contain $actual")
    }

    companion object {
        private const val mvnWinCommand = "mvn.cmd"
        private const val mvnCommonCommand = "mvn"

        @JvmStatic
        private fun gradleVersions(): Stream<Arguments> = Stream.of(
            Arguments.of("7.6"),
            Arguments.of("8.6")
        )
    }
}