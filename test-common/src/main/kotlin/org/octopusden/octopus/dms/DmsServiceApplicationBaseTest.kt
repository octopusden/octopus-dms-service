package org.octopusden.octopus.dms

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.octopusden.octopus.dms.client.DmsServiceUploadingClient
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsStatusesDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentsDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.PropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.VersionsDTO
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import org.octopusden.octopus.dms.exception.VersionFormatIsNotValidException
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.octopusden.octopus.dms.exception.IllegalComponentRenamingException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DmsServiceApplicationBaseTest {
    abstract val client: DmsServiceUploadingClient

    val objectMapper = ObjectMapper().apply {
        this.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        this.registerModule(KotlinModule.Builder().build())
    }

    private val artifactoryHost = "http://localhost:8081"

    private val dmsDbConnection: Connection = DriverManager.getConnection(
        "jdbc:postgresql://localhost:5432/dms",
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
        it.executeUpdate("INSERT INTO component_version(component_id, minor_version, version)" +
                " SELECT id, '${version.minorVersion}', '${version.buildVersion}'" +
                " FROM component WHERE name = '$component'")
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
            .map { it.removePrefix("$artifactoryHost/artifactory/") }
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
            assertEquals(
                artifact,
                client.uploadArtifact(releaseNotesCoordinates, it, releaseReleaseNotesFileName)
            )
        }
        client.downloadArtifact(artifact.id).use { response ->
            releaseNotesRELEASE.openStream().use {
                assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
            }
        }
        client.deleteArtifact(artifact.id)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getArtifact(artifact.id)
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
        client.deleteArtifact(artifact.id)
        client.downloadArtifact(artifact.id).use {
            assertEquals(404, it.status())
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
    fun testGetValidationConfiguration() = assertEquals(
        getResource("configuration.json").openStream().use {
            objectMapper.readValue(it, PropertiesDTO::class.java)
        },
        client.getConfiguration()
    )

    @Test
    fun testGetComponents() = assertEquals(
        getResource("components.json").openStream().use {
            objectMapper.readValue(it, ComponentsDTO::class.java)
        },
        client.getComponents()
    )

    @Test
    fun testGetComponentMinorVersions() {
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        client.registerComponentVersionArtifact(eeComponent, eeComponentRCVersion0354.releaseVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        client.registerComponentVersionArtifact(eeComponent, eeComponentRCVersion0355.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        insertVersion(eeComponent, eeComponentBuildVersion0356) //NOTE: getComponentMinorVersions does not check build status
        assertEquals(
            getResource("component-minor-versions.json").openStream().use {
                objectMapper.readValue(it, object : TypeReference<List<String>>() {})
            },
            client.getComponentMinorVersions(eeComponent)
        )
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testGetComponentMinorVersionsForNonEEComponent(component: String) {
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentMinorVersions(component)
        }
    }

    @Test
    fun testGetComponentVersions() {
        assertEquals(
            ComponentVersionsStatusesDTO(emptyList()),
            client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion)
        )
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        client.registerComponentVersionArtifact(eeComponent, eeComponentRCVersion0354.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        insertVersion(eeComponent, eeComponentBuildVersion0354)
        client.registerComponentVersionArtifact(eeComponent, eeComponentRCVersion0355.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        insertVersion(eeComponent, eeComponentBuildVersion0355)
        assertEquals(
            ComponentVersionsStatusesDTO(emptyList()),
            client.getComponentVersions(eeComponent, eeComponentBuildVersion0356.minorVersion)
        )
        assertEquals(
            getResource("component-versions.json").openStream().use {
                objectMapper.readValue(it, ComponentVersionsStatusesDTO::class.java)
            },
            client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion)
        )
        assertEquals(
            getResource("component-versions-no-rc.json").openStream().use {
                objectMapper.readValue(it, ComponentVersionsStatusesDTO::class.java)
            },
            client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion, false)
        )
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testGetComponentVersionsForNonEEComponent(component: String) {
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersions(component, eeComponentBuildVersion0356.minorVersion)
        }
    }

    @ParameterizedTest
    @MethodSource("releaseArtifacts")
    fun testRenameComponent(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        val componentVersionArtifact = client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.DISTRIBUTION))
        val componentVersionArtifacts = client.getComponentVersionArtifacts(eeComponent, eeComponentReleaseVersion0354.releaseVersion, ArtifactType.DISTRIBUTION)
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        updateName(eeComponent, "some-$eeComponent")
        assertThrows(NotFoundException::class.java) {
            client.getComponentVersionArtifact("some-$eeComponent", eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        }
        var newComponent = client.renameComponent("some-$eeComponent", eeComponent)
        assertEquals(eeComponent, newComponent.name)
        // Check that the operation(renaming) is idempotent
        newComponent = client.renameComponent("some-$eeComponent", eeComponent)
        assertEquals(eeComponent, newComponent.name)
        // Check exception to rename unexisting component
        assertThrowsExactly(NotFoundException::class.java) {
            client.renameComponent(eeComponent, eeComponent)
        }
        // Check an exception, when both old and new component names exist in the system
        val artifact2 = client.addArtifact(artifactCoordinates)
        client.registerComponentVersionArtifact("some-$eeComponent", eeComponentReleaseVersion0353.buildVersion, artifact2.id, RegisterArtifactDTO(ArtifactType.NOTES))
        assertThrows(IllegalComponentRenamingException::class.java) {
            client.renameComponent(eeComponent, "some-$eeComponent")
        }
        // Check that artifact with new component name is available
        client.downloadComponentVersionArtifact(newComponent.name, eeComponentReleaseVersion0354.releaseVersion, artifact.id).use { response ->
            assertTrue(response.body().asInputStream().readBytes().isNotEmpty())
        }
    }

    @Test
    fun testGetPreviousLinesLatestVersions() {
        assertEquals(
            VersionsDTO(emptyList()),
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentRCVersion0355.buildVersion)
        )
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0353.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        insertVersion(eeComponent, eeComponentBuildVersion0353)
        client.registerComponentVersionArtifact(eeComponent, eeComponentRCVersion0353.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        insertVersion(eeComponent, eeComponentBuildVersion0354)
        client.registerComponentVersionArtifact(eeComponent, eeComponentRCVersion0354.releaseVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        assertEquals(
            VersionsDTO(emptyList()),
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentReleaseVersion0353.buildVersion)
        )
        assertEquals(
            getResource("previous-lines-latest-versions.json").openStream().use {
                objectMapper.readValue(it, VersionsDTO::class.java)
            },
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentRCVersion0355.releaseVersion)
        )
        assertEquals(
            getResource("previous-lines-latest-versions-include-rc.json").openStream().use {
                objectMapper.readValue(it, VersionsDTO::class.java)
            },
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentRCVersion0355.buildVersion, true)
        )
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testGetPreviousLinesLatestVersionsForNonEEComponent(component: String) {
        assertThrowsExactly(NotFoundException::class.java) {
            client.getPreviousLinesLatestVersions(component, eeComponentBuildVersion0356.minorVersion)
        }
    }

    @Test
    fun testGetPreviousLinesLatestVersionsForInvalidVersion() {
        assertThrowsExactly(VersionFormatIsNotValidException::class.java) {
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentBuildVersion0356.minorVersion)
        }
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentBuildVersion0356.buildVersion)
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentBuildVersion0356.releaseVersion)
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentNonExistsVersion0357.buildVersion)
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.getPreviousLinesLatestVersions(eeComponent, eeComponentNonExistsVersion0357.releaseVersion)
        }
    }

    @Test
    fun testRegisterUploadedArtifact() {
        val releaseNotesRELEASE = getResource(releaseReleaseNotesFileName)
        val artifact = releaseNotesRELEASE.openStream().use {
            client.uploadArtifact(releaseNotesCoordinates, it, releaseReleaseNotesFileName)
        }
        assertEquals(0, client.getComponentVersionArtifacts(eeComponent, eeComponentReleaseVersion0354.releaseVersion, ArtifactType.NOTES).artifacts.size)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        }
        val componentVersionArtifact = client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        val componentVersionArtifacts = client.getComponentVersionArtifacts(eeComponent, eeComponentReleaseVersion0354.releaseVersion, ArtifactType.NOTES)
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        assertTrue(componentVersionArtifacts.artifacts.first() == componentVersionArtifact)
        assertEquals(componentVersionArtifact, client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id))
        client.downloadComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id).use { response ->
            releaseNotesRELEASE.openStream().use {
                assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
            }
        }
        client.deleteComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        }
        client.downloadComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id).use {
            assertEquals(403, it.status()) //access is denied because there is no info about artifact type
        }
        assertEquals(artifact, client.getArtifact(artifact.id))
    }

    @ParameterizedTest
    @MethodSource("stagingArtifacts")
    fun testRegisterStagingArtifact(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(UnableToFindArtifactException::class.java) {
            client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.DISTRIBUTION))
        }
    }

    @ParameterizedTest
    @MethodSource("releaseArtifacts")
    fun testRegisterReleaseArtifact(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertEquals(0, client.getComponentVersionArtifacts(eeComponent, eeComponentReleaseVersion0354.releaseVersion, ArtifactType.DISTRIBUTION).artifacts.size)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id)
        }
        val componentVersionArtifact = client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.DISTRIBUTION))
        val componentVersionArtifacts = client.getComponentVersionArtifacts(eeComponent, eeComponentReleaseVersion0354.releaseVersion, ArtifactType.DISTRIBUTION)
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        assertTrue(componentVersionArtifacts.artifacts.first() == componentVersionArtifact)
        assertEquals(componentVersionArtifact, client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id))
        client.deleteComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id)
        }
        assertEquals(artifact, client.getArtifact(artifact.id))
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testRegisterArtifactForNonEEComponent(component: String) {
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(component, eeComponentReleaseVersion0354.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.DISTRIBUTION))
        }
    }

    @ParameterizedTest
    @MethodSource("releaseArtifacts")
    fun testRegisterDistributionArtifactForRCVersion(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.registerComponentVersionArtifact(eeComponent, eeComponentRCVersion0355.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.DISTRIBUTION))
        }
    }

    @ParameterizedTest
    @MethodSource("releaseArtifacts")
    fun testRegisterReportArtifactForBuildVersion(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.registerComponentVersionArtifact(eeComponent, eeComponentBuildVersion0356.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.REPORT))
        }
    }

    @Test
    fun testRegisterInvalidVersion() {
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        assertThrowsExactly(VersionFormatIsNotValidException::class.java) {
            client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.minorVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(eeComponent, eeComponentNonExistsVersion0357.buildVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(eeComponent, eeComponentNonExistsVersion0357.releaseVersion, artifact.id, RegisterArtifactDTO(ArtifactType.NOTES))
        }
    }

    @Test
    fun testRegisterInvalidArtifact() {
        assertThrowsExactly(NotFoundException::class.java) {
            client.registerComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, -1, RegisterArtifactDTO(ArtifactType.DISTRIBUTION))
        }
    }

    //<editor-fold defaultstate="collapsed" desc="Test Data">
    companion object {
        data class Version(val minorVersion: String, val buildVersion: String, val releaseVersion: String)
        const val eeComponent = "ee-component"
        val eeComponentReleaseVersion0353 = Version("03.53.31", "03.53.30.31-1", "03.53.30.31")
        val eeComponentBuildVersion0353 = Version("03.53.31", "03.53.30.42-1", "03.53.30.42")
        val eeComponentRCVersion0353 = Version("03.53.31", "03.53.30.53-1", "03.53.30.53")
        val eeComponentBuildVersion0354 = Version("03.54.31", "03.54.30.42-1", "03.54.30.42")
        val eeComponentRCVersion0354 = Version("03.54.31", "03.54.30.53-1", "03.54.30.53")
        val eeComponentReleaseVersion0354 = Version("03.54.31", "03.54.30.64-1", "03.54.30.64")
        val eeComponentBuildVersion0355 = Version("03.55.31", "03.55.30.42-1", "03.55.30.42")
        val eeComponentRCVersion0355 = Version("03.55.31", "03.55.30.53-1", "03.55.30.53")
        val eeComponentBuildVersion0356 = Version("03.56.31", "03.56.30.42-1", "03.56.30.42")
        val eeComponentNonExistsVersion0357 = Version("03.57.31", "03.57.30.64-1", "03.57.30.64")
        const val devReleaseNotesFileName = "release-notes-RC.txt"
        const val releaseReleaseNotesFileName = "release-notes-RELEASE.txt"
        val releaseNotesCoordinates = MavenArtifactCoordinatesDTO(GavDTO("test.upload", "release-notes", "1.0", "txt", "en"))
        val devMavenDistributionCoordinates = MavenArtifactCoordinatesDTO(GavDTO("test.add", "distribution", "1.0", "zip", "dev"))
        val releaseMavenDistributionCoordinates = MavenArtifactCoordinatesDTO(GavDTO("test.add", "distribution", "1.0", "zip", "release"))
        val devDebianDistributionCoordinates = DebianArtifactCoordinatesDTO("pool/t/test-add-distribution/test-add-distribution-dev_1.0-1_amd64.deb")
        val releaseDebianDistributionCoordinates = DebianArtifactCoordinatesDTO("pool/t/test-add-distribution/test-add-distribution-release_1.0-1_amd64.deb")
        val devRpmDistributionCoordinates = RpmArtifactCoordinatesDTO("test-add-distribution/test-add-distribution-dev-1.0-1.el8.x86_64.rpm")
        val releaseRpmDistributionCoordinates = RpmArtifactCoordinatesDTO("test-add-distribution/test-add-distribution-release-1.0-1.el8.x86_64.rpm")

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
            )
        )

        @JvmStatic
        private fun nonEEComponents(): Stream<Arguments> = Stream.of(
            Arguments.of("ie-component"),
            Arguments.of("ei-component"),
            Arguments.of("ii-component"),
            Arguments.of("no-component")
        )

        @JvmStatic
        private fun artifacts(): Stream<Arguments> = Stream.of(
            Arguments.of(devMavenDistributionCoordinates),
            Arguments.of(releaseMavenDistributionCoordinates),
            Arguments.of(devDebianDistributionCoordinates),
            Arguments.of(releaseDebianDistributionCoordinates),
            Arguments.of(devRpmDistributionCoordinates),
            Arguments.of(releaseRpmDistributionCoordinates)
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
        private fun releaseArtifacts(): Stream<Arguments> = Stream.of(
            Arguments.of(releaseMavenDistributionCoordinates),
            Arguments.of(releaseDebianDistributionCoordinates),
            Arguments.of(releaseRpmDistributionCoordinates)
        )
    }
    //</editor-fold>
}