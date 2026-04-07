package org.octopusden.octopus.dms

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.octopusden.octopus.dms.client.DmsServiceUploadingClient
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.PatchComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactCoordinatesDTO
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.DMSException
import org.octopusden.octopus.dms.exception.IllegalComponentTypeException
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import org.octopusden.octopus.dms.exception.VersionPublishedException

@Suppress("SqlDialectInspection", "SameParameterValue")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DmsServiceApplicationBaseTest {
    abstract val client: DmsServiceUploadingClient

    val objectMapper = ObjectMapper().apply {
        this.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        this.registerModule(KotlinModule.Builder().build())
    }

    private val artifactoryHost = System.getProperty("test.artifactory-host")
        ?: throw Exception("System property 'test.artifactory-host' must be defined")
    private val postgresHost = System.getProperty("test.postgres-host")
        ?: throw Exception("System property 'test.postgres-host' must be defined")

    private val dmsDbConnection: Connection = DriverManager.getConnection(
        "jdbc:postgresql://${postgresHost}/dms",
        Properties().apply {
            this["user"] = "dms"
            this["password"] = "dms"
        }
    )

    @BeforeEach
    fun clean() {
        dmsDbConnection.createStatement().use {
            it.executeUpdate("TRUNCATE component, artifact CASCADE")
        }
    }

    @AfterAll
    fun close() = dmsDbConnection.close()

    private fun insertVersion(component: String, version: Version) = dmsDbConnection.createStatement().use {
        it.executeUpdate(
            "INSERT INTO component_version(component_id, minor_version, version, published)" +
                    " SELECT id, '${version.minorVersion}', '${version.buildVersion}', false" +
                    " FROM component WHERE name = '$component'"
        )
    }

    private fun updateSha256(artifactId: Long, sha256: String) = dmsDbConnection.createStatement().use {
        it.executeUpdate("UPDATE artifact SET sha256 = '$sha256' WHERE id = $artifactId")
    }

    /**
     * Internal method to update component name in the database
     * @param name the old name
     * @param newName the new name
     */
    private fun updateName(name: String, newName: String) = dmsDbConnection.createStatement().use {
        it.executeUpdate("UPDATE component SET name = '$newName' WHERE name = '$name'")
    }

    fun getResource(path: String) = this.javaClass.classLoader.getResource(path)!!

    @ParameterizedTest
    @MethodSource("repositories")
    fun testGetRepositories(repositoryType: RepositoryType, expectedRepositories: List<String>) {
        val repositories = client.getRepositories(repositoryType)
            .map { it.removePrefix("http://$artifactoryHost/artifactory/") }
        assertIterableEquals(
            expectedRepositories.sortedDescending(),
            repositories.sortedDescending()
        )
    }

    @Test
    fun testUploadArtifact() {
        assertThrowsExactly(NotFoundException::class.java) {
            client.findArtifact(releaseNotesCoordinates)
        }
        val releaseNotesRC = getResource(devReleaseNotesFileName)
        val artifact = releaseNotesRC.openStream().use {
            client.uploadArtifact(releaseNotesCoordinates, it, devReleaseNotesFileName, true)
        }
        assertEquals(true, artifact.uploaded)
        assertEquals(RepositoryType.MAVEN, artifact.repositoryType)
        assertEquals(releaseNotesCoordinates.gav, artifact.gav)
        assertEquals(artifact, client.findArtifact(releaseNotesCoordinates))
        assertEquals(artifact, client.getArtifact(artifact.id))
        client.downloadArtifact(artifact.id).use { response ->
            releaseNotesRC.openStream().use {
                assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
            }
        }
        val releaseNotesRELEASE = getResource(releaseReleaseNotesFileName)
        assertThrowsExactly(ArtifactAlreadyExistsException::class.java) {
            releaseNotesRELEASE.openStream().use {
                client.uploadArtifact(releaseNotesCoordinates, it, releaseReleaseNotesFileName, true)
            }
        }
        releaseNotesRELEASE.openStream().use {
            with(client.uploadArtifact(releaseNotesCoordinates, it, releaseReleaseNotesFileName)) {
                assertEquals(artifact.id, id)
                assertEquals(artifact.repositoryType, repositoryType)
                assertEquals(artifact.uploaded, uploaded)
                assertNotEquals(artifact.sha256, sha256)
                assertEquals(artifact.gav, gav)
            }
        }
        client.downloadArtifact(artifact.id).use { response ->
            releaseNotesRELEASE.openStream().use {
                assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
            }
        }
    }

    @ParameterizedTest
    @MethodSource("artifacts")
    fun testAddArtifacts(artifactCoordinates: ArtifactCoordinatesDTO) {
        assertThrowsExactly(NotFoundException::class.java) {
            client.findArtifact(artifactCoordinates)
        }
        val artifact = client.addArtifact(artifactCoordinates, true)
        assertEquals(false, artifact.uploaded)
        assertEquals(artifactCoordinates.repositoryType, artifact.repositoryType)
        assertEquals(artifact, client.findArtifact(artifactCoordinates))
        assertEquals(artifact, client.getArtifact(artifact.id))
        val artifactFile = client.downloadArtifact(artifact.id).use { response ->
            response.body().asInputStream().readBytes()
        }
        assertThrowsExactly(ArtifactAlreadyExistsException::class.java) {
            client.addArtifact(artifactCoordinates, true)
        }
        assertEquals(artifact, client.addArtifact(artifactCoordinates))
        client.downloadArtifact(artifact.id).use { response ->
            assertArrayEquals(artifactFile, response.body().asInputStream().readBytes())
        }
    }

    @ParameterizedTest
    @MethodSource("invalidArtifacts")
    fun testAddInvalidArtifacts(artifactCoordinates: ArtifactCoordinatesDTO) {
        assertThrowsExactly(UnableToFindArtifactException::class.java) {
            client.addArtifact(artifactCoordinates)
        }
    }

    @Test
    fun testUploadDownloadSbomArtifact() {
        val sbomResource = getResource(TEST_SBOM_FILE_NAME)
        val originalContent = sbomResource.openStream().use { it.readBytes() }

        val uploadedSbomArtifact = sbomResource.openStream().use { inputStream ->
            client.uploadArtifact(
                artifactCoordinates = sbomCoordinates,
                file = inputStream,
                fileName = TEST_SBOM_FILE_NAME,
                failOnAlreadyExists = true
            )
        }

        assertTrue(uploadedSbomArtifact.uploaded)
        assertEquals(RepositoryType.MAVEN, uploadedSbomArtifact.repositoryType)
        assertEquals(sbomCoordinates.gav, uploadedSbomArtifact.gav)

        val registeredArtifact = client.registerComponentVersionArtifact(
            componentName = eeComponent,
            version = eeComponentReleaseVersion0354.releaseVersion,
            artifactId = uploadedSbomArtifact.id,
            registerArtifactDTO = RegisterArtifactDTO(ArtifactType.SBOM)
        )

        val componentArtifacts = client.getComponentVersionArtifacts(
            componentName = eeComponent,
            version = eeComponentReleaseVersion0354.releaseVersion,
            type = ArtifactType.SBOM
        )

        assertEquals(1, componentArtifacts.artifacts.size)
        assertEquals(registeredArtifact.id, componentArtifacts.artifacts.first().id)

        val downloadedContent = client.downloadArtifact(uploadedSbomArtifact.id).use { response ->
            sbomResource.openStream().use {
                response.body().asInputStream().readBytes()
            }
        }

        assertArrayEquals(
            originalContent,
            downloadedContent,
            "The contents of the downloaded SBOM do not match the original file!"
        )
    }

    @ParameterizedTest
    @MethodSource
    fun testRegisterUploadedArtifact(version: Version) {
        val releaseNotesRELEASE = getResource(releaseReleaseNotesFileName)
        val artifact = releaseNotesRELEASE.openStream().use {
            client.uploadArtifact(releaseNotesCoordinates, it, releaseReleaseNotesFileName)
        }
        assertEquals(
            0,
            client.getComponentVersionArtifacts(
                eeComponent,
                version.releaseVersion,
                ArtifactType.NOTES
            ).artifacts.size
        )
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, version.releaseVersion, artifact.id)
        }
        val componentVersionArtifact = client.registerComponentVersionArtifact(
            eeComponent,
            version.releaseVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        val componentVersionArtifacts = client.getComponentVersionArtifacts(
            eeComponent,
            version.releaseVersion,
            ArtifactType.NOTES
        )
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        assertTrue(componentVersionArtifacts.artifacts.first() == componentVersionArtifact.toShortDTO())
        assertEquals(
            componentVersionArtifact,
            client.getComponentVersionArtifact(eeComponent, version.releaseVersion, artifact.id)
        )
        client.downloadComponentVersionArtifact(eeComponent, version.releaseVersion, artifact.id)
            .use { response ->
                releaseNotesRELEASE.openStream().use {
                    assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
                }
            }
        client.deleteComponentVersionArtifact(eeComponent, version.releaseVersion, artifact.id)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, version.releaseVersion, artifact.id)
        }
        client.downloadComponentVersionArtifact(eeComponent, version.releaseVersion, artifact.id)
            .use {
                assertEquals(403, it.status()) //access is denied because there is no info about artifact type
            }
        assertEquals(artifact, client.getArtifact(artifact.id))
    }

    @ParameterizedTest
    @MethodSource
    fun testPatchComponentVersion(rcVersion: Version, releaseVersion: Version, hotfix: Boolean) {
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.patchComponentVersion(
                eeComponent,
                rcVersion.buildVersion,
                PatchComponentVersionDTO(true)
            )
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.patchComponentVersion(
                eeComponent,
                releaseVersion.buildVersion,
                PatchComponentVersionDTO(true)
            )
        }
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        val artifactDTO = client.registerComponentVersionArtifact(
            eeComponent,
            releaseVersion.releaseVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
        )
        assertThrowsExactly(VersionPublishedException::class.java) {
            client.patchComponentVersion(
                eeComponent,
                releaseVersion.buildVersion,
                PatchComponentVersionDTO(true)
            )
        }
        val dependencies = mapOf(
            "dependency1" to "1.0.1",
            "dependency2" to "2.0.1",
            "dependency3" to "3.0.1"
        )
        dependencies.forEach { (componentName, version) ->
            client.registerComponentVersionArtifact(
                componentName,
                version,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
        assertThrowsExactly(VersionPublishedException::class.java) {
            client.patchComponentVersion(
                eeComponent,
                releaseVersion.releaseVersion,
                PatchComponentVersionDTO(true)
            )
        }
        dependencies.forEach { (componentName, version) ->
            client.patchComponentVersion(componentName, version, PatchComponentVersionDTO(true))
        }
        val versionDTO = ComponentVersionDTO(
            eeComponent,
            releaseVersion.buildVersion,
            true,
            ComponentVersionStatus.RELEASE,
            hotfix
        )
        assertEquals(
            versionDTO,
            client.patchComponentVersion(
                eeComponent,
                releaseVersion.releaseVersion,
                PatchComponentVersionDTO(true)
            )
        )
        assertEquals(
            versionDTO,
            client.patchComponentVersion(
                eeComponent,
                releaseVersion.releaseVersion,
                PatchComponentVersionDTO(true)
            )
        )
        assertThrowsExactly(VersionPublishedException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                releaseVersion.buildVersion,
                client.addArtifact(releaseDockerDistributionCoordinates).id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
        assertThrowsExactly(ArtifactAlreadyExistsException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                releaseVersion.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION),
                true
            )
        }
        assertEquals(
            artifactDTO,
            client.registerComponentVersionArtifact(
                eeComponent,
                releaseVersion.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        )
        dependencies.forEach { (componentName, version) ->
            assertThrowsExactly(VersionPublishedException::class.java) {
                client.deleteComponentVersionArtifact(componentName, version, artifact.id)
            }
            assertThrowsExactly(VersionPublishedException::class.java) {
                client.patchComponentVersion(componentName, version, PatchComponentVersionDTO(false))
            }
        }
        assertEquals(
            ComponentVersionDTO(
                eeComponent,
                releaseVersion.buildVersion,
                false,
                ComponentVersionStatus.RELEASE,
                hotfix
            ),
            client.patchComponentVersion(
                eeComponent,
                releaseVersion.releaseVersion,
                PatchComponentVersionDTO(false)
            )
        )
        dependencies.forEach { (componentName, version) ->
            assertThrowsExactly(VersionPublishedException::class.java) {
                client.deleteComponentVersionArtifact(componentName, version, artifact.id)
            }
            client.patchComponentVersion(componentName, version, PatchComponentVersionDTO(false))
            client.deleteComponentVersionArtifact(componentName, version, artifact.id)
        }
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testPatchComponentVersionForNonEEComponent(component: String, exception: Class<out DMSException>) {
        assertThrowsExactly(exception) {
            client.patchComponentVersion(component, ANY_VERSION, PatchComponentVersionDTO(true))
        }
    }

    @ParameterizedTest
    @MethodSource
    fun testGetComponentVersionDependencies(version: Version) {
        assertEquals(
            ComponentVersionsDTO(emptyList()),
            client.getComponentVersions(eeComponent, version.minorVersion)
        )
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        client.registerComponentVersionArtifact(
            eeComponent,
            version.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        mapOf(
            eeComponent to version.buildVersion,
            "dependency1" to "1.0.1",
            "dependency2" to "2.0.1"
        ).forEach { (componentName, version) ->
            client.registerComponentVersionArtifact(
                componentName,
                version,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.NOTES)
            )
        }
        val dependencies = client.getComponentVersionDependencies(
            eeComponent, version.buildVersion
        )
        assertIterableEquals(
            listOf("dependency1", "dependency2"),
            dependencies.map { it.component }
        )
        assertThrowsExactly(IllegalComponentTypeException::class.java) {
            client.getComponentVersionDependencies(
                eeClientSpecificComponent, ANY_VERSION
            )
        }
    }

    @ParameterizedTest
    @MethodSource("stagingArtifactsReleaseVersions")
    fun testRegisterStagingArtifact(artifactCoordinates: ArtifactCoordinatesDTO, version: Version) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(UnableToFindArtifactException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                version.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("artifactsReleaseVersions")
    fun testRegisterReleaseArtifact(artifactCoordinates: ArtifactCoordinatesDTO, version: Version) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertEquals(
            0,
            client.getComponentVersionArtifacts(
                eeComponent,
                version.releaseVersion,
                ArtifactType.DISTRIBUTION
            ).artifacts.size
        )
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, version.buildVersion, artifact.id)
        }
        assertTrue(
            client.getComponentVersionArtifacts(
                eeComponent,
                version.buildVersion
            ).artifacts.isEmpty()
        )
        assertFalse(
            client.getComponentVersions(
                eeComponent, version.minorVersion
            ).versions.map { it.version }.contains(version.buildVersion)
        )
        val componentVersionArtifact = client.registerComponentVersionArtifact(
            eeComponent,
            version.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
        )
        assertTrue(
            client.getComponentVersionArtifacts(
                eeComponent,
                version.releaseVersion,
                ArtifactType.NOTES
            ).artifacts.isEmpty()
        )
        val componentVersionArtifacts = client.getComponentVersionArtifacts(
            eeComponent,
            version.releaseVersion,
            ArtifactType.DISTRIBUTION
        )
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        assertTrue(componentVersionArtifacts.artifacts.first() == componentVersionArtifact.toShortDTO())
        assertTrue(
            client.getComponentVersions(
                eeComponent, version.minorVersion
            ).versions.map { it.version }.contains(version.buildVersion)
        )
        assertEquals(
            componentVersionArtifact,
            client.getComponentVersionArtifact(eeComponent, version.buildVersion, artifact.id)
        )
        client.deleteComponentVersionArtifact(eeComponent, version.releaseVersion, artifact.id)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, version.buildVersion, artifact.id)
        }
        assertTrue(
            client.getComponentVersionArtifacts(
                eeComponent,
                version.buildVersion
            ).artifacts.isEmpty()
        )
        assertFalse(
            client.getComponentVersions(
                eeComponent, version.minorVersion
            ).versions.map { it.version }.contains(version.buildVersion)
        )
        assertEquals(artifact, client.getArtifact(artifact.id))
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testRegisterArtifactForNonEEComponent(component: String, exception: Class<out DMSException>) {
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        assertThrowsExactly(exception) {
            client.registerComponentVersionArtifact(
                component,
                ANY_VERSION,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("distributionArtifactRCVersions")
    fun testRegisterDistributionArtifactForRCVersion(artifactCoordinates: ArtifactCoordinatesDTO, version: Version) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                version.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("reportArtifactBuildVersions")
    fun testRegisterReportArtifactForBuildVersion(artifactCoordinates: ArtifactCoordinatesDTO, version: Version) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                version.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.REPORT)
            )
        }
    }

    @Test
    fun testRegisterInvalidVersion() {
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.minorVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.NOTES)
            )
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentNonExistsVersion0357.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.NOTES)
            )
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentNonExistsVersion0357.releaseVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.NOTES)
            )
        }
    }

    @Test
    fun testRegisterInvalidArtifact() {
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                -1,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
    }

    //<editor-fold defaultstate="collapsed" desc="Test Data">
    companion object {
        data class Version(val minorVersion: String, val buildVersion: String, val releaseVersion: String)

        const val ANY_VERSION = "ANY_VERSION"
        const val eeComponent = "ee-component"
        const val eeClientSpecificComponent = "ee-client-specific-component"

        val eeComponentReleaseVersion0353 = Version("03.53.31", "03.53.30.31-1", "03.53.30.31")
        val eeComponentBuildVersion0353 = Version("03.53.31", "03.53.30.42-1", "03.53.30.42")
        val eeComponentRCVersion0353 = Version("03.53.31", "03.53.30.53-1", "03.53.30.53")
        val eeComponentHotfixReleaseVersion0353 = Version("03.53.31", "03.53.30.61-1", "03.53.30.61")
        val eeComponentHotfixBuildVersion0353 = Version("03.53.31", "03.53.30.72-1", "03.53.30.72")
        val eeComponentHotfixRCVersion0353 = Version("03.53.31", "03.53.30.83-1", "03.53.30.83")

        val eeComponentBuildVersion0354 = Version("03.54.31", "03.54.30.42-1", "03.54.30.42")
        val eeComponentRCVersion0354 = Version("03.54.31", "03.54.30.53-1", "03.54.30.53")
        val eeComponentReleaseVersion0354 = Version("03.54.31", "03.54.30.64-1", "03.54.30.64")
        val eeComponentHotfixBuildVersion0354 = Version("03.54.31", "03.54.30.65-1", "03.54.30.65")
        val eeComponentHotfixRCVersion0354 = Version("03.54.31", "03.54.30.70-1", "03.54.30.70")
        val eeComponentHotfixReleaseVersion0354 = Version("03.54.31", "03.54.30.74-1", "03.54.30.74")

        val eeComponentBuildVersion0355 = Version("03.55.31", "03.55.30.42-1", "03.55.30.42")
        val eeComponentRCVersion0355 = Version("03.55.31", "03.55.30.53-1", "03.55.30.53")
        val eeComponentHotfixBuildVersion0355 = Version("03.55.31", "03.55.30.63-1", "03.55.30.63")
        val eeComponentHotfixRCVersion0355 = Version("03.55.31", "03.55.30.73-1", "03.55.30.73")

        val eeComponentBuildVersion0356 = Version("03.56.31", "03.56.30.42-1", "03.56.30.56")
        val eeComponentHotfixBuildVersion0356 = Version("03.56.31", "03.56.30.52-1", "03.56.30.52")
        val eeComponentNonExistsVersion0357 = Version("03.57.31", "03.57.30.64-1", "03.57.30.64")

        const val devReleaseNotesFileName = "release-notes-RC.txt"
        const val releaseReleaseNotesFileName = "release-notes-RELEASE.txt"

        const val TEST_SBOM_FILE_NAME = "test-sbom.json"

        val releaseNotesCoordinates =
            MavenArtifactCoordinatesDTO(GavDTO("test.upload", "release-notes", "1.0", "txt", "en"))
        val devMavenDistributionCoordinates =
            MavenArtifactCoordinatesDTO(GavDTO("test.add", "distribution", "1.0", "zip", "dev"))
        val releaseMavenDistributionCoordinates =
            MavenArtifactCoordinatesDTO(GavDTO("test.add", "distribution", "1.0", "zip", "release"))
        val sbomCoordinates = MavenArtifactCoordinatesDTO(
            GavDTO(
                groupId = "test.sbom",
                artifactId = "test-sbom",
                version = "1.0.0",
                packaging = "json",
                classifier = "sbom"
            )
        )
        val devDebianDistributionCoordinates =
            DebianArtifactCoordinatesDTO("pool/t/test-add-distribution/test-add-distribution-dev_1.0-1_amd64.deb")
        val releaseDebianDistributionCoordinates =
            DebianArtifactCoordinatesDTO("pool/t/test-add-distribution/test-add-distribution-release_1.0-1_amd64.deb")
        val devRpmDistributionCoordinates =
            RpmArtifactCoordinatesDTO("test-add-distribution/test-add-distribution-dev-1.0-1.el8.x86_64.rpm")
        val releaseRpmDistributionCoordinates =
            RpmArtifactCoordinatesDTO("test-add-distribution/test-add-distribution-release-1.0-1.el8.x86_64.rpm")
        val releaseDockerDistributionCoordinates =
            DockerArtifactCoordinatesDTO("test/test-component", "1.0")

        private val DEV_GAV = devMavenDistributionCoordinates.gav
        private val DEV_ARTIFACTS_COORDINATES_GAV = "${DEV_GAV.groupId}:${DEV_GAV.artifactId}:${DEV_GAV.version}:${DEV_GAV.packaging}" + (DEV_GAV.classifier?.let { c -> ":$c" } ?: "")
        val DEV_ARTIFACTS_COORDINATES = DEV_ARTIFACTS_COORDINATES_GAV.replace(":1.0:", ":")

        private val RELEASE_GAV = releaseMavenDistributionCoordinates.gav
        private val RELEASE_ARTIFACTS_COORDINATES_GAV = "${RELEASE_GAV.groupId}:${RELEASE_GAV.artifactId}:${RELEASE_GAV.version}:${RELEASE_GAV.packaging}" + (RELEASE_GAV.classifier?.let { c -> ":$c" } ?: "")
        val RELEASE_ARTIFACTS_COORDINATES = RELEASE_ARTIFACTS_COORDINATES_GAV.replace(":1.0:", ":")

        val DEV_DEB_ARTIFACTS_COORDINATES = devDebianDistributionCoordinates.deb
        val RELEASE_DEB_ARTIFACTS_COORDINATES = releaseDebianDistributionCoordinates.deb
        val DEV_RPM_ARTIFACTS_COORDINATES = devRpmDistributionCoordinates.rpm
        val RELEASE_RPM_ARTIFACTS_COORDINATES = releaseRpmDistributionCoordinates.rpm
        val RELEASE_DOCKER_ARTIFACTS_COORDINATES = "${releaseDockerDistributionCoordinates.image}:${releaseDockerDistributionCoordinates.tag}"

        @JvmStatic
        private fun repositories(): Stream<Arguments> = Stream.of(
            Arguments.of(
                RepositoryType.MAVEN, listOf("maven-upload-repo-local", "maven-release-repo-local")
            ),
            Arguments.of(
                RepositoryType.DEBIAN, listOf("debian-release-repo-local-1", "debian-release-repo-local-2")
            ),
            Arguments.of(
                RepositoryType.RPM, listOf("rpm-release-repo-local")
            ),
            Arguments.of(
                RepositoryType.DOCKER, listOf("docker-repo-local")
            )
        )

        @JvmStatic
        private fun nonEEComponents(): Stream<Arguments> = Stream.of(
            Arguments.of("ie-component", IllegalComponentTypeException::class.java),
            Arguments.of("ei-component", IllegalComponentTypeException::class.java),
            Arguments.of("ii-component", IllegalComponentTypeException::class.java),
            Arguments.of("no-component", NotFoundException::class.java)
        )

        @JvmStatic
        private fun artifacts(): Stream<Arguments> = Stream.of(
            Arguments.of(devMavenDistributionCoordinates),
            Arguments.of(releaseMavenDistributionCoordinates),
            Arguments.of(devDebianDistributionCoordinates),
            Arguments.of(releaseDebianDistributionCoordinates),
            Arguments.of(devRpmDistributionCoordinates),
            Arguments.of(releaseRpmDistributionCoordinates),
            Arguments.of(releaseDockerDistributionCoordinates)
        )

        @JvmStatic
        private fun invalidArtifacts(): Stream<Arguments> = Stream.of(
            Arguments.of(
                MavenArtifactCoordinatesDTO(GavDTO("test.add.invalid", "distribution", "1.0", "zip"))
            ),
            Arguments.of(
                DebianArtifactCoordinatesDTO("pool/t/test-add-distribution/test-add-distribution-invalid_1.0-1_amd64.deb")
            ),
            Arguments.of(
                RpmArtifactCoordinatesDTO("test-add-distribution/test-add-distribution-invalid-1.0-1.el8.x86_64.rpm")
            )
        )

        @JvmStatic
        private fun stagingArtifacts(): Stream<Arguments> = Stream.of(
            Arguments.of(devMavenDistributionCoordinates),
            Arguments.of(devDebianDistributionCoordinates),
            Arguments.of(devRpmDistributionCoordinates)
        )

        @JvmStatic
        private fun stagingArtifactsReleaseVersions(): Stream<Arguments> =
            stagingArtifacts().flatMap { artifact ->
                Stream.of(
                    Arguments.of(artifact.get()[0], eeComponentReleaseVersion0354),
                    Arguments.of(artifact.get()[0], eeComponentHotfixReleaseVersion0354)
                )
            }

        @JvmStatic
        private fun releaseArtifacts(): Stream<Arguments> = Stream.of(
            Arguments.of(releaseMavenDistributionCoordinates),
            Arguments.of(releaseDebianDistributionCoordinates),
            Arguments.of(releaseRpmDistributionCoordinates)
        )

        @JvmStatic
        private fun artifactsReleaseVersions(): Stream<Arguments> =
            releaseArtifacts().flatMap { artifact ->
                Stream.of(
                    Arguments.of(artifact.get()[0], eeComponentReleaseVersion0354),
                    Arguments.of(artifact.get()[0], eeComponentHotfixReleaseVersion0354)
                )
            }

        @JvmStatic
        private fun distributionArtifactRCVersions(): Stream<Arguments> =
            releaseArtifacts().flatMap { artifact ->
                Stream.of(
                    Arguments.of(artifact.get()[0], eeComponentRCVersion0355),
                    Arguments.of(artifact.get()[0], eeComponentHotfixRCVersion0355)
                )
            }

        @JvmStatic
        private fun reportArtifactBuildVersions(): Stream<Arguments> =
            releaseArtifacts().flatMap { artifact ->
                Stream.of(
                    Arguments.of(artifact.get()[0], eeComponentBuildVersion0356),
                    Arguments.of(artifact.get()[0], eeComponentHotfixBuildVersion0355)
                )
            }

        @JvmStatic
        private fun testGetComponentMinorVersions(): Stream<Arguments> = Stream.of(
            Arguments.of(eeComponentReleaseVersion0354, eeComponentRCVersion0354, eeComponentRCVersion0355, eeComponentBuildVersion0356),
            Arguments.of(eeComponentHotfixReleaseVersion0354, eeComponentHotfixRCVersion0354, eeComponentHotfixRCVersion0355, eeComponentHotfixBuildVersion0356)
        )

        @JvmStatic
        private fun testGetComponentVersions(): Stream<Arguments> = Stream.of(
            Arguments.of(
                eeComponentBuildVersion0354, eeComponentRCVersion0354, eeComponentReleaseVersion0354,
                eeComponentBuildVersion0355, eeComponentRCVersion0355, eeComponentBuildVersion0356,
                "component-versions"
            ),
            Arguments.of(
                eeComponentHotfixBuildVersion0354, eeComponentHotfixRCVersion0354, eeComponentHotfixReleaseVersion0354,
                eeComponentHotfixBuildVersion0355, eeComponentHotfixRCVersion0355, eeComponentHotfixBuildVersion0356,
                "component-versions-hotfix"
            )
        )

        @JvmStatic
        private fun testGetPreviousLinesLatestVersions(): Stream<Arguments> = Stream.of(
            Arguments.of(
                eeComponentBuildVersion0353, eeComponentRCVersion0353, eeComponentReleaseVersion0353,
                eeComponentBuildVersion0354, eeComponentRCVersion0354, eeComponentReleaseVersion0354,
                eeComponentRCVersion0355, "previous-lines-latest-versions"
            ),
            Arguments.of(
                eeComponentHotfixBuildVersion0353, eeComponentHotfixRCVersion0353, eeComponentHotfixReleaseVersion0353,
                eeComponentHotfixBuildVersion0354, eeComponentHotfixRCVersion0354, eeComponentHotfixReleaseVersion0354,
                eeComponentHotfixRCVersion0355, "previous-lines-latest-versions-hotfix"
            )
        )

        @JvmStatic
        private fun testRegisterUploadedArtifact(): Stream<Arguments> = Stream.of(
            Arguments.of(eeComponentReleaseVersion0354),
            Arguments.of(eeComponentHotfixReleaseVersion0354)
        )

        @JvmStatic
        private fun testPatchComponentVersion(): Stream<Arguments> = Stream.of(
            Arguments.of(eeComponentRCVersion0354, eeComponentReleaseVersion0354, false),
            Arguments.of(eeComponentHotfixRCVersion0354, eeComponentHotfixReleaseVersion0354, true)
        )

        @JvmStatic
        private fun testGetComponentVersionDependencies(): Stream<Arguments> = Stream.of(
            Arguments.of(eeComponentReleaseVersion0354),
            Arguments.of(eeComponentHotfixReleaseVersion0354)
        )
    }
    //</editor-fold>
}