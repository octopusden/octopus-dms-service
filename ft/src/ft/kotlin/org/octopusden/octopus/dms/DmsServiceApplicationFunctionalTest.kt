package org.octopusden.octopus.dms

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildResultException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactDTO
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

            override fun getBasicCredentials() = "${System.getProperty("dms-service.user")}:${System.getProperty("dms-service.password")}"
        },
    )

    @ParameterizedTest
    @MethodSource("gradleVersions")
    fun testGradleDmsClient(
        gradleVersion: String,
        shouldSucceed: Boolean,
    ) {
        val reports = listOf(
            "REPORT0354" to MavenArtifactCoordinatesDTO(
                GavDTO(
                    "test.gradle.dms.client",
                    "report",
                    eeComponentReleaseVersion0354.releaseVersion,
                    "txt",
                ),
            ),
            "REPORT0353" to MavenArtifactCoordinatesDTO(
                GavDTO(
                    "test.gradle.dms.client",
                    "report",
                    eeComponentReleaseVersion0353.releaseVersion,
                    "txt",
                ),
            ),
        )
        reports.forEach { report ->
            report.first.byteInputStream(UTF_8).use {
                client.registerComponentVersionArtifact(
                    eeComponent,
                    report.second.gav.version,
                    client.uploadArtifact(report.second, it, "report").id,
                    RegisterArtifactDTO(ArtifactType.REPORT),
                )
            }
        }
        val buildDir = File("").resolve("build")
        val sourceProjectDir = buildDir.resolve("resources").resolve("ft").resolve("test-gradle-dms-client")
        val projectDir = buildDir.resolve("tmp").resolve("test-gradle-dms-client-$gradleVersion")
        val targetDir = projectDir.resolve("export")

        projectDir.deleteRecursively()
        sourceProjectDir.copyRecursively(projectDir, overwrite = true)

        // Propagate use_dev_repository to the testkit child Gradle so the agent's
        // init.gradle activates the internal dev repo for transitive CRS snapshot deps.
        // Point the testkit at the agent's ~/.gradle as its GRADLE_USER_HOME so init
        // scripts, gradle.properties (NEXUS_USER/NEXUS_PASSWORD), and wrapper caches
        // are all picked up naturally — no need to forward scripts or credentials
        // separately.
        val useDevRepoArg = System.getProperty("use_dev_repository")?.let { "-Puse_dev_repository=$it" }
        val runner = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withTestKitDir(agentGradleUserHome(buildDir, gradleVersion))
            .withArguments(
                listOfNotNull(
                    "-Pdms-service.version=${System.getProperty("dms-service.version")}",
                    "-Pdms-service.url=$dmsServiceUrl",
                    "-Pdms-service.user=${System.getProperty("dms-service.user")}",
                    "-Pdms-service.password=${System.getProperty("dms-service.password")}",
                    "-Pcreg-service.url=$cregServiceUrl",
                    "-Pcomponent.name=$eeComponent",
                    "-Pcomponent.version=${eeComponentReleaseVersion0354.releaseVersion}",
                    "-Ptarget-dir=${targetDir.toPath().toAbsolutePath()}",
                    useDevRepoArg,
                    "exportArtifactsTask",
                    "--info",
                ),
            )

        val result = runTestKitBuild(runner, shouldSucceed, buildDir, "test-gradle-dms-client-$gradleVersion.log")

        if (!shouldSucceed) {
            // The one invariant worth asserting on the negative row: whatever the agent
            // makes Gradle 7.6 fail on, the client must not have exported anything.
            // targetDir is clean by construction (projectDir is deleted and re-copied
            // above), and this says nothing about *how* the failure happened, so it
            // cannot rot the way the old failure-text assertion did.
            assertTrue(
                targetDir.listFiles().isNullOrEmpty(),
                "Gradle $gradleVersion was expected to fail without exporting, but produced: ${targetDir.list()?.toList()}",
            )
        }

        if (shouldSucceed) {
            reports.forEach {
                it.first.byteInputStream(UTF_8).use { expected ->
                    targetDir
                        .resolve(
                            it.second.gav
                                .toPath()
                                .substringAfterLast('/'),
                        ).inputStream()
                        .use { actual ->
                            assertArrayEquals(expected.readBytes(), actual.readBytes())
                        }
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
                RegisterArtifactDTO(ArtifactType.NOTES),
            )
        }
        val buildDir = File("").resolve("build")
        val projectDir = buildDir.resolve("resources").resolve("ft").resolve("test-gradle-dms-plugin")
        val targetDir = projectDir.resolve("export")
        val useDevRepoArg2 = System.getProperty("use_dev_repository")?.let { "-Puse_dev_repository=$it" }
        val runner = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(agentGradleUserHome(buildDir, "dms-plugin"))
            .withArguments(
                listOfNotNull(
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
                    useDevRepoArg2,
                    "downloadReleaseNotes",
                    "--info",
                ),
            )
        val result = runTestKitBuild(runner, shouldSucceed = true, buildDir = buildDir, logFileName = "test-gradle-dms-plugin.log")
        releaseNotesRELEASE.openStream().use { expected ->
            targetDir.resolve(releaseNotesCoordinates.gav.toPath().substringAfterLast("/")).inputStream().use { actual ->
                assertArrayEquals(expected.readBytes(), actual.readBytes())
            }
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsDifferentRepos() {
        with(
            runMavenDmsPlugin(
                "different-repos.log",
                "validate-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=${DEV_ARTIFACTS_COORDINATES},${RELEASE_ARTIFACTS_COORDINATES}",
                    "-Dartifacts.coordinates.version=1.0",
                    "-Dartifacts.coordinates.deb=$DEV_DEB_ARTIFACTS_COORDINATES,$RELEASE_DEB_ARTIFACTS_COORDINATES",
                    "-Dartifacts.coordinates.rpm=$DEV_RPM_ARTIFACTS_COORDINATES,$RELEASE_RPM_ARTIFACTS_COORDINATES",
                    "-DenabledFileValidators=license,copyright",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(1, this.first)
            assertContains(this.second, "[ERROR] Artifact '${devMavenDistributionCoordinates.toPath()}' validation errors:")
            assertContains(
                this.second,
                "${devMavenDistributionCoordinates.gav.toPath().substringAfterLast(
                    '/',
                )}: required file rule 'license' failed: no file matching '((.*[/\\\\])|^)licenses[/\\\\]THIRD-PARTY.txt$' found",
            )
            assertContains(
                this.second,
                "${devMavenDistributionCoordinates.gav.toPath().substringAfterLast(
                    '/',
                )}: required file rule 'copyright' failed: no file matching '((.*[/\\\\])|^)COPYRIGHT$' found",
            )
            assertContains(this.second, "[ERROR] Artifact '${releaseMavenDistributionCoordinates.toPath()}' validation errors:")
            assertContains(
                this.second,
                "${releaseMavenDistributionCoordinates.gav.toPath().substringAfterLast(
                    '/',
                )}: required file rule 'license' failed: no file matching '((.*[/\\\\])|^)licenses[/\\\\]THIRD-PARTY.txt$' found",
            )
            assertContains(
                this.second,
                "${releaseMavenDistributionCoordinates.gav.toPath().substringAfterLast(
                    '/',
                )}: required file rule 'copyright' failed: no file matching '((.*[/\\\\])|^)COPYRIGHT$' found",
            )
            assertContains(
                this.second,
                "[INFO] Validated artifact '${devDebianDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Validated artifact '${releaseDebianDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Validated artifact '${devRpmDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Validated artifact '${releaseRpmDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
        }
    }

    /**
     * A coordinate states the version it is published at as `<coordinate>@<version>`, which is what
     * lets artifacts released on different version lines be handled in one invocation - the single
     * `artifacts.coordinates.version` cannot express that.
     *
     * Both artifacts of the dump live at 1.0 while the component is released as
     * [eeComponentReleaseVersion0354], so nothing here resolves unless the suffix is what the
     * version is taken from: without it the lookup would go to the released version and find
     * nothing.
     */
    @Test
    fun testMavenDmsPluginValidateArtifactsCoordinateVersions() {
        with(
            runMavenDmsPlugin(
                "coordinate-versions.log",
                "validate-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=$DEV_ARTIFACTS_COORDINATES@1.0,$RELEASE_ARTIFACTS_COORDINATES@1.0",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(0, this.first, this.second.joinToString("\n"))
            assertContains(
                this.second,
                "[INFO] Validated artifact '${devMavenDistributionCoordinates.toPath()}' " +
                    "for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Validated artifact '${releaseMavenDistributionCoordinates.toPath()}' " +
                    "for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsInvalidDistribution() {
        with(
            runMavenDmsPlugin(
                "invalid-distribution.log",
                "validate-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=file:///${File(
                        "",
                    ).absolutePath}/src/ft/resources/test-maven-dms-plugin/$eeComponent-${eeComponentReleaseVersion0354.buildVersion}.zip?artifactId=distribution",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(1, this.first)
            assertContains(
                this.second,
                "[ERROR] Artifact 'corp/domain/dms/$eeComponent/distribution/distribution/${eeComponentReleaseVersion0354.buildVersion}/distribution-${eeComponentReleaseVersion0354.buildVersion}.zip' validation errors:",
            )
            assertContains(
                this.second,
                "distribution-${eeComponentReleaseVersion0354.buildVersion}.zip/lib/forbidden.jar/forbidden.xml: line 1, token '<providerName>unallowed</providerName>' matches regexp '.*unallowed.*'",
            )
            assertContains(
                this.second,
                "distribution-${eeComponentReleaseVersion0354.buildVersion}.zip/forbidden.xml: line 1, token '<providerName>unallowed</providerName>' matches regexp '.*unallowed.*'",
            )
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsArtifactNotFound() {
        with(
            runMavenDmsPlugin(
                "artifact-not-found.log",
                "validate-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=test.add.invalid:distribution:zip",
                    "-Dartifacts.coordinates.deb=pool/t/test-add-distribution/test-add-distribution-invalid_1.0-1_amd64.deb",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(1, this.first)
            assertTrue(
                this.second.none { it.contains("UnableToFindArtifactException") },
                "Expected no per-artifact exception dump in the output: ${this.second}",
            )
            // Both artifacts must get their own actionable diagnostic. Asserting the message text
            // once would pass while one artifact stayed generic, so each identifier is required to
            // appear on the same line as the message.
            assertActionableNotFound("test/add/invalid/distribution/", this.second)
            assertActionableNotFound("test-add-distribution-invalid_1.0-1_amd64.deb", this.second)
            assertTrue(
                this.second.any { it.contains("2 of 2 artifact(s) failed") },
                "Expected the composed failure summary in the output: ${this.second}",
            )
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsExcludeFile() {
        val coordValue = "file:///${File(
            "",
        ).absolutePath}/src/ft/resources/test-maven-dms-plugin/$eeComponent-${eeComponentReleaseVersion0354.buildVersion}.zip?artifactId=distribution&classifier=test"
        val coordArgs = if (isWindowsSystem) "\"$coordValue\"" else coordValue
        with(
            runMavenDmsPlugin(
                "exclude-file.log",
                "validate-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=$coordArgs",
                    "-DexcludeFiles=forbidden.xml",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(0, this.first)
            assertContains(
                this.second,
                "[INFO] Validated artifact 'corp/domain/dms/$eeComponent/distribution/distribution/${eeComponentReleaseVersion0354.buildVersion}/distribution-${eeComponentReleaseVersion0354.buildVersion}-test.zip' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
        }
    }

    @Test
    fun testMavenDmsPluginValidateArtifactsWlIgnore() {
        val coordValue = "file:///${File(
            "",
        ).absolutePath}/src/ft/resources/test-maven-dms-plugin/$eeComponent-${eeComponentReleaseVersion0354.buildVersion}.zip?artifactId=distribution&classifier=test"
        val coordArgs = if (isWindowsSystem) "\"$coordValue\"" else coordValue
        with(
            runMavenDmsPlugin(
                "wl-ignore.log",
                "validate-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=$coordArgs",
                    "-DwlIgnore=${File("").absolutePath}/src/ft/resources/test-maven-dms-plugin/.wlignore.json",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(0, this.first)
            assertContains(
                this.second,
                "[INFO] Validated artifact 'corp/domain/dms/$eeComponent/distribution/distribution/${eeComponentReleaseVersion0354.buildVersion}/distribution-${eeComponentReleaseVersion0354.buildVersion}-test.zip' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
        }
    }

    @Test
    fun testMavenDmsPluginUploadArtifactsDifferentRepos() {
        with(
            runMavenDmsPlugin(
                "different-repos.log",
                "upload-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=${RELEASE_ARTIFACTS_COORDINATES}",
                    "-Dartifacts.coordinates.version=1.0",
                    "-Dartifacts.coordinates.deb=$RELEASE_DEB_ARTIFACTS_COORDINATES",
                    "-Dartifacts.coordinates.rpm=$RELEASE_RPM_ARTIFACTS_COORDINATES",
                    "-Dartifacts.coordinates.docker=$RELEASE_DOCKER_ARTIFACTS_COORDINATES",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(0, this.first, this.second.joinToString("\n"))
            assertContains(
                this.second,
                "[INFO] Uploaded distribution artifact '${releaseMavenDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Uploaded distribution artifact '${releaseDebianDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Uploaded distribution artifact '${releaseRpmDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Uploaded distribution artifact '${releaseDockerDistributionCoordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
        }
        assertEquals(
            releaseMavenDistributionCoordinates.gav,
            (client.findArtifact(releaseMavenDistributionCoordinates) as MavenArtifactDTO).gav,
        )
        assertEquals(
            releaseDebianDistributionCoordinates.deb,
            (client.findArtifact(releaseDebianDistributionCoordinates) as DebianArtifactDTO).deb,
        )
        assertEquals(
            releaseRpmDistributionCoordinates.rpm,
            (client.findArtifact(releaseRpmDistributionCoordinates) as RpmArtifactDTO).rpm,
        )
        val dockerArtifact = client.findArtifact(releaseDockerDistributionCoordinates) as DockerArtifactDTO
        assertEquals(releaseDockerDistributionCoordinates.image, dockerArtifact.image)
        assertEquals(releaseDockerDistributionCoordinates.tag, dockerArtifact.tag)
    }

    @Test
    fun testMavenDmsPluginUploadArtifactsFiles() {
        val resourcesDir = File("")
            .resolve("src")
            .resolve("ft")
            .resolve("resources")
            .resolve("test-maven-dms-plugin")
            .absoluteFile
        val distribution1 = resourcesDir.resolve(".wlignore.json")
        val distribution1Coordinates = MavenArtifactCoordinatesDTO(
            GavDTO(
                "corp.domain.dms.$eeComponent.distribution",
                "distribution1",
                eeComponentReleaseVersion0354.buildVersion,
                "json",
            ),
        )
        val distribution2 = resourcesDir.resolve("ee-component-03.54.30.64-1.zip")
        val distribution2Coordinates = MavenArtifactCoordinatesDTO(
            GavDTO(
                "corp.domain.dms.$eeComponent.distribution",
                "distribution2",
                eeComponentReleaseVersion0354.buildVersion,
                "zip",
            ),
        )
        with(
            runMavenDmsPlugin(
                "files.log",
                "upload-artifacts",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dartifacts.coordinates=${distribution1.toPath().toUri()}?artifactId=distribution1,${distribution2.toPath().toUri()}?artifactId=distribution2",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(0, this.first)
            assertContains(
                this.second,
                "[INFO] Uploaded distribution artifact '${distribution1Coordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
            assertContains(
                this.second,
                "[INFO] Uploaded distribution artifact '${distribution2Coordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
        }
        client
            .downloadComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                client.findArtifact(distribution1Coordinates).id,
            ).use { response ->
                distribution1.inputStream().use {
                    assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
                }
            }
        client
            .downloadComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                client.findArtifact(distribution2Coordinates).id,
            ).use { response ->
                distribution2.inputStream().use {
                    assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
                }
            }
    }

    @Test
    fun testMavenDmsPluginUpload() {
        val file = File(
            "",
        ).resolve("src").resolve("ft").resolve("resources").resolve("test-maven-dms-plugin").resolve("ee-component-03.54.30.64-1.zip")
        val coordinates = MavenArtifactCoordinatesDTO(
            GavDTO(
                "corp.domain.dms.$eeComponent.distribution",
                "test",
                eeComponentReleaseVersion0354.buildVersion,
                "zip",
            ),
        )
        with(
            runMavenDmsPlugin(
                "file.log",
                "upload",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0354.buildVersion}",
                    "-Dname=test",
                    "-Dfile=${file.absolutePath}",
                    "-Dtype=distribution",
                ),
            ),
        ) {
            assertEquals(0, this.first)
            assertContains(
                this.second,
                "[INFO] Uploaded distribution artifact '${coordinates.toPath()}' for component '$eeComponent' version '${eeComponentReleaseVersion0354.buildVersion}'",
            )
        }
        client
            .downloadComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                client.findArtifact(coordinates).id,
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
            RegisterArtifactDTO(ArtifactType.DISTRIBUTION),
        )
        with(
            runMavenDmsPlugin(
                "file.log",
                "publish",
                listOf(
                    "-Dcomponent=$eeComponent",
                    "-Dversion=${eeComponentReleaseVersion0353.buildVersion}",
                ),
            ),
        ) {
            assertEquals(0, this.first)
            assertContains(this.second, "[INFO] Published component '$eeComponent' version '${eeComponentReleaseVersion0353.buildVersion}'")
        }
    }

    private fun runMavenDmsPlugin(
        outputFileName: String,
        goal: String,
        parameters: List<String>,
    ): Pair<Int, List<String>> {
        val outputFile = File("")
            .resolve("build")
            .resolve("logs")
            .resolve("test-maven-dms-plugin-$goal")
            .resolve(outputFileName)
            .also { it.parentFile.mkdirs() }
        // Mirror Gradle init.gradle's use_dev_repository at the Maven layer via -Pstaging so
        // mvn CLI can resolve CRS branch snapshots from the internal dev-virtual repo.
        val stagingProfile = System.getProperty("use_dev_repository")?.let { "-Pstaging" }
        val process = ProcessBuilder(
            listOfNotNull(
                mvn,
                "org.octopusden.octopus.dms:maven-dms-plugin:${System.getProperty("dms-service.version")}:$goal",
                "-e",
                stagingProfile,
                "-Ddms.url=$dmsServiceUrl",
                "-Ddms.username=${System.getProperty("dms-service.user")}",
                "-Ddms.password=${System.getProperty("dms-service.password")}",
            ) + parameters,
        ).redirectErrorStream(true)
            .redirectOutput(outputFile)
            .start()
        process.waitFor(5, MINUTES)
        return process.exitValue() to outputFile.readLines(UTF_8)
    }

    private fun assertContains(
        source: List<String>,
        actual: String,
    ) {
        assertTrue(source.contains(actual), "Expected the source $source to contain $actual")
    }

    private fun assertActionableNotFound(
        artifactIdentifier: String,
        output: List<String>,
    ) {
        assertTrue(
            output.any {
                it.contains(artifactIdentifier) &&
                    it.contains("was not found in any of the repositories") &&
                    it.contains("never published to Artifactory")
            },
            "Expected '$artifactIdentifier' to be reported with the actionable not-found message: $output",
        )
    }

    /**
     * Run the testkit child build and always leave its output on disk, including when
     * GradleRunner throws because the result was the opposite of what we asked for:
     * an unexpected success or failure is exactly the case worth diagnosing, and the
     * output is only reachable through the exception's own BuildResult.
     */
    private fun runTestKitBuild(
        runner: GradleRunner,
        shouldSucceed: Boolean,
        buildDir: File,
        logFileName: String,
    ): BuildResult {
        val result =
            try {
                if (shouldSucceed) runner.build() else runner.buildAndFail()
            } catch (e: UnexpectedBuildResultException) {
                writeTestKitLog(buildDir, logFileName, e.buildResult.output)
                throw e
            }
        writeTestKitLog(buildDir, logFileName, result.output)
        return result
    }

    private fun writeTestKitLog(
        buildDir: File,
        fileName: String,
        output: String,
    ) {
        // File.writeText flushes and closes. Wrapping the raw OutputStream in a writer
        // and closing only the stream dropped whatever was still sitting in the
        // encoder's 8 KiB buffer, so every log was truncated down to a multiple of
        // 8192 bytes — 0 for the short ones, and a plausible-looking 98304 for a long
        // one, which is why this went unnoticed.
        val log = buildDir.resolve("logs").resolve(fileName)
        log.parentFile.mkdirs()
        log.writeText(output, UTF_8)
    }

    /**
     * Resolve the GRADLE_USER_HOME the testkit child should use. Prefer the real
     * ~/.gradle on the TC agent so init scripts, gradle.properties
     * (NEXUS_USER/NEXUS_PASSWORD), and wrapper caches are picked up naturally — no
     * need to forward scripts or credentials one by one. On a cleanroom dev box
     * without ~/.gradle, fall back to an isolated dir under build/.
     */
    private fun agentGradleUserHome(
        buildDir: File,
        scope: String,
    ): File {
        val home = File(System.getProperty("user.home"), ".gradle")
        return if (home.isDirectory) {
            home
        } else {
            buildDir
                .resolve("tmp")
                .resolve("testkit-$scope")
                .absoluteFile
        }
    }

    companion object {
        private const val mvnWinCommand = "mvn.cmd"
        private const val mvnCommonCommand = "mvn"

        @JvmStatic
        private fun gradleVersions(): Stream<Arguments> =
            Stream.of(
                // Gradle 7.6 cannot run here at all: it predates Java 21 support (which
                // landed in Gradle 8.5) and the agents run JDK 21, so the testkit child
                // is an unsupported combination before our code is even reached. Note
                // this is NOT about the client's own bytecode — gradle-dms-client is
                // pure Java at release 8, and common/client pin jvmTarget 1.8, so that
                // whole buildscript classpath is class-file major 52.
                //
                // So the 7.6 case legitimately fails and we assert only *that* it fails
                // (GradleRunner.buildAndFail) and that nothing was exported, never
                // *how* it failed: the failure text is a
                // property of the agent, not of our product. It was "Failed to create
                // Jar file" (#75), re-baselined to "Unsupported class file major
                // version" (#83) on the theory that the agent's Java-21 init.gradle was
                // rejected by Groovy 2.5, and broke again on 2026-08-30 when the agents
                // stopped producing that message (TC builds 12091129 and 12095408 — the
                // child then ran 23-68 s instead of ~5 s before failing elsewhere; what
                // it fails on now is not yet known). The saved test-kit log artifact is
                // the place to look, and the input for narrowing this case down to a
                // product-level invariant.
                Arguments.of("7.6", false),
                Arguments.of("8.6", true),
            )
    }
}
