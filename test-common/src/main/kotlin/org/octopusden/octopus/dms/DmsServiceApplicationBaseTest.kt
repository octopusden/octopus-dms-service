package org.octopusden.octopus.dms

import com.fasterxml.jackson.core.type.TypeReference
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
import org.junit.jupiter.api.Assertions.assertThrows
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
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionStatus
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentsDTO
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.PatchComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.PropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.VersionsDTO
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.ArtifactChecksumChangedException
import org.octopusden.octopus.dms.exception.DMSException
import org.octopusden.octopus.dms.exception.IllegalComponentRenamingException
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
    fun testRegisterUploadedArtifactWithChangedChecksum() {
        val releaseNotesRC = getResource(devReleaseNotesFileName)
        val artifact = releaseNotesRC.openStream().use {
            client.uploadArtifact(releaseNotesCoordinates, it, devReleaseNotesFileName, true)
        }
        updateSha256(artifact.id, "some-other-sha256")
        assertThrowsExactly(ArtifactChecksumChangedException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.NOTES)
            )
        }
        releaseNotesRC.openStream().use {
            client.uploadArtifact(releaseNotesCoordinates, it, devReleaseNotesFileName, false)
        }
        with(client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )) {
            this as MavenArtifactFullDTO
            assertEquals(artifact.id, id)
            assertEquals(artifact.sha256, sha256)
            assertEquals(artifact.gav, gav)
        }
    }

    @Test
    fun testRegisterAddedArtifactWithChangedChecksum() {
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        updateSha256(artifact.id, "some-other-sha256")
        assertThrowsExactly(ArtifactChecksumChangedException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
        client.addArtifact(releaseMavenDistributionCoordinates)
        with(client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )) {
            assertEquals(artifact.id, id)
            assertEquals(artifact.sha256, sha256)
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
    fun testGetComponents() {
        val eeComponentsDTO = getResource("ee-components.json").openStream().use {
            objectMapper.readValue(it, ComponentsDTO::class.java)
        }

        assertEquals(
            eeComponentsDTO.components,
            client.getComponents(ComponentRequestFilter()).components
        )

        arrayOf(null, true, false).forEach { solution ->
            assertEquals(
                eeComponentsDTO.components.filter {
                    solution?.let { solutionValue -> it.solution == solutionValue } ?: true
                },
                client.getComponents(ComponentRequestFilter(solution = solution)).components
            )
        }

        val componentsDTO = getResource("components.json").openStream().use {
            objectMapper.readValue(it, ComponentsDTO::class.java)
        }

        assertEquals(
            componentsDTO.components,
            client.getComponents(ComponentRequestFilter(explicit = false)).components
        )
    }

    @Test
    fun testGetComponentMinorVersions() {
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentRCVersion0354.releaseVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentRCVersion0355.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        insertVersion(
            eeComponent,
            eeComponentBuildVersion0356
        ) //NOTE: getComponentMinorVersions does not check build status
        assertEquals(
            getResource("component-minor-versions.json").openStream().use {
                objectMapper.readValue(it, object : TypeReference<List<String>>() {})
            },
            client.getComponentMinorVersions(eeComponent)
        )
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testGetComponentMinorVersionsForNonEEComponent(component: String, exception: Class<out DMSException>) {
        assertThrowsExactly(exception) {
            client.getComponentMinorVersions(component)
        }
    }

    @Test
    fun testGetComponentVersions() {
        assertEquals(
            ComponentVersionsDTO(emptyList()),
            client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion)
        )
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentRCVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        insertVersion(eeComponent, eeComponentBuildVersion0354)
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentRCVersion0355.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        insertVersion(eeComponent, eeComponentBuildVersion0355)
        assertEquals(
            ComponentVersionsDTO(emptyList()),
            client.getComponentVersions(eeComponent, eeComponentBuildVersion0356.minorVersion)
        )
        assertEquals(
            getResource("component-versions.json").openStream().use {
                objectMapper.readValue(it, ComponentVersionsDTO::class.java)
            },
            client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion)
        )
        assertEquals(
            getResource("component-versions-no-rc.json").openStream().use {
                objectMapper.readValue(it, ComponentVersionsDTO::class.java)
            },
            client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion, false)
        )
    }

    @ParameterizedTest
    @MethodSource("nonEEComponents")
    fun testGetComponentVersionsForNonEEComponent(component: String, exception: Class<out DMSException>) {
        assertThrowsExactly(exception) {
            client.getComponentVersions(component, ANY_VERSION)
        }
    }

    @ParameterizedTest
    @MethodSource("releaseArtifacts")
    fun testRenameComponent(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
        )
        val componentVersionArtifacts = client.getComponentVersionArtifacts(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            ArtifactType.DISTRIBUTION
        )
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        updateName(eeComponent, "some-$eeComponent")
        assertThrows(NotFoundException::class.java) {
            client.getComponentVersionArtifact(
                "some-$eeComponent",
                eeComponentReleaseVersion0354.releaseVersion,
                artifact.id
            )
        }
        client.renameComponent("some-$eeComponent", eeComponent)
        client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion)
        // Check that the operation(renaming) is idempotent
        client.renameComponent("some-$eeComponent", eeComponent)
        // Check exception to rename unexisting component
        assertThrowsExactly(IllegalComponentRenamingException::class.java) {
            client.renameComponent(eeComponent, eeComponent)
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.renameComponent("some-$eeComponent", "some-$eeComponent")
        }
        // Check an exception, when both old and new component names exist in the system
        val artifact2 = client.addArtifact(artifactCoordinates)
        client.registerComponentVersionArtifact(
            eeClientSpecificComponent,
            eeComponentReleaseVersion0353.buildVersion,
            artifact2.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        assertThrows(IllegalComponentRenamingException::class.java) {
            client.renameComponent(eeComponent, eeClientSpecificComponent)
        }
        // Check that artifact with new component name is available
        client.downloadComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
            .use { response ->
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
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0353.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        insertVersion(eeComponent, eeComponentBuildVersion0353)
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentRCVersion0353.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        insertVersion(eeComponent, eeComponentBuildVersion0354)
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentRCVersion0354.releaseVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
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
    fun testGetPreviousLinesLatestVersionsForNonEEComponent(component: String, exception: Class<out DMSException>) {
        assertThrowsExactly(exception) {
            client.getPreviousLinesLatestVersions(component, ANY_VERSION)
        }
    }

    @Test
    fun testGetPreviousLinesLatestVersionsForInvalidVersion() {
        assertThrowsExactly(NotFoundException::class.java) {
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
        assertEquals(
            0,
            client.getComponentVersionArtifacts(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                ArtifactType.NOTES
            ).artifacts.size
        )
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        }
        val componentVersionArtifact = client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        val componentVersionArtifacts = client.getComponentVersionArtifacts(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            ArtifactType.NOTES
        )
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        assertTrue(componentVersionArtifacts.artifacts.first() == componentVersionArtifact.toShortDTO())
        assertEquals(
            componentVersionArtifact,
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        )
        client.downloadComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
            .use { response ->
                releaseNotesRELEASE.openStream().use {
                    assertArrayEquals(it.readBytes(), response.body().asInputStream().readBytes())
                }
            }
        client.deleteComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        }
        client.downloadComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
            .use {
                assertEquals(403, it.status()) //access is denied because there is no info about artifact type
            }
        assertEquals(artifact, client.getArtifact(artifact.id))
    }

    @Test
    fun testPatchComponentVersion() {
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.patchComponentVersion(
                eeComponent,
                eeComponentRCVersion0355.buildVersion,
                PatchComponentVersionDTO(true)
            )
        }
        assertThrowsExactly(NotFoundException::class.java) {
            client.patchComponentVersion(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                PatchComponentVersionDTO(true)
            )
        }
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        val artifactDTO = client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
        )
        assertThrowsExactly(VersionPublishedException::class.java) {
            client.patchComponentVersion(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
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
                eeComponentReleaseVersion0354.releaseVersion,
                PatchComponentVersionDTO(true)
            )
        }
        dependencies.forEach { (componentName, version) ->
            client.patchComponentVersion(componentName, version, PatchComponentVersionDTO(true))
        }
        val versionDTO = ComponentVersionDTO(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            true,
            ComponentVersionStatus.RELEASE
        )
        assertEquals(
            versionDTO,
            client.patchComponentVersion(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                PatchComponentVersionDTO(true)
            )
        )
        assertEquals(
            versionDTO,
            client.patchComponentVersion(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                PatchComponentVersionDTO(true)
            )
        )
        assertThrowsExactly(VersionPublishedException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                client.addArtifact(releaseDockerDistributionCoordinates).id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
        assertThrowsExactly(ArtifactAlreadyExistsException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION),
                true
            )
        }
        assertEquals(
            artifactDTO,
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
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
                eeComponentReleaseVersion0354.buildVersion,
                false,
                ComponentVersionStatus.RELEASE
            ),
            client.patchComponentVersion(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
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

    @Test
    fun testGetComponentVersionDependencies() {
        assertEquals(
            ComponentVersionsDTO(emptyList()),
            client.getComponentVersions(eeComponent, eeComponentReleaseVersion0354.minorVersion)
        )
        val artifact = client.addArtifact(releaseMavenDistributionCoordinates)
        client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.NOTES)
        )
        mapOf(
            eeComponent to eeComponentReleaseVersion0354.buildVersion,
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
            eeComponent, eeComponentReleaseVersion0354.buildVersion
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
    @MethodSource("stagingArtifacts")
    fun testRegisterStagingArtifact(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(UnableToFindArtifactException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("releaseArtifacts")
    fun testRegisterReleaseArtifact(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertEquals(
            0,
            client.getComponentVersionArtifacts(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                ArtifactType.DISTRIBUTION
            ).artifacts.size
        )
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id)
        }
        assertTrue(
            client.getComponentVersionArtifacts(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion
            ).artifacts.isEmpty()
        )
        assertFalse(
            client.getComponentVersions(
                eeComponent, eeComponentReleaseVersion0354.minorVersion
            ).versions.map { it.version }.contains(eeComponentReleaseVersion0354.buildVersion)
        )
        val componentVersionArtifact = client.registerComponentVersionArtifact(
            eeComponent,
            eeComponentReleaseVersion0354.buildVersion,
            artifact.id,
            RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
        )
        assertTrue(
            client.getComponentVersionArtifacts(
                eeComponent,
                eeComponentReleaseVersion0354.releaseVersion,
                ArtifactType.NOTES
            ).artifacts.isEmpty()
        )
        val componentVersionArtifacts = client.getComponentVersionArtifacts(
            eeComponent,
            eeComponentReleaseVersion0354.releaseVersion,
            ArtifactType.DISTRIBUTION
        )
        assertEquals(1, componentVersionArtifacts.artifacts.size)
        assertTrue(componentVersionArtifacts.artifacts.first() == componentVersionArtifact.toShortDTO())
        assertTrue(
            client.getComponentVersions(
                eeComponent, eeComponentReleaseVersion0354.minorVersion
            ).versions.map { it.version }.contains(eeComponentReleaseVersion0354.buildVersion)
        )
        assertEquals(
            componentVersionArtifact,
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id)
        )
        client.deleteComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.releaseVersion, artifact.id)
        assertThrowsExactly(NotFoundException::class.java) {
            client.getComponentVersionArtifact(eeComponent, eeComponentReleaseVersion0354.buildVersion, artifact.id)
        }
        assertTrue(
            client.getComponentVersionArtifacts(
                eeComponent,
                eeComponentReleaseVersion0354.buildVersion
            ).artifacts.isEmpty()
        )
        assertFalse(
            client.getComponentVersions(
                eeComponent, eeComponentReleaseVersion0354.minorVersion
            ).versions.map { it.version }.contains(eeComponentReleaseVersion0354.buildVersion)
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
    @MethodSource("releaseArtifacts")
    fun testRegisterDistributionArtifactForRCVersion(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentRCVersion0355.buildVersion,
                artifact.id,
                RegisterArtifactDTO(ArtifactType.DISTRIBUTION)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("releaseArtifacts")
    fun testRegisterReportArtifactForBuildVersion(artifactCoordinates: ArtifactCoordinatesDTO) {
        val artifact = client.addArtifact(artifactCoordinates)
        assertThrowsExactly(IllegalVersionStatusException::class.java) {
            client.registerComponentVersionArtifact(
                eeComponent,
                eeComponentBuildVersion0356.buildVersion,
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
        val eeComponentBuildVersion0354 = Version("03.54.31", "03.54.30.42-1", "03.54.30.42")
        val eeComponentRCVersion0354 = Version("03.54.31", "03.54.30.53-1", "03.54.30.53")
        val eeComponentReleaseVersion0354 = Version("03.54.31", "03.54.30.64-1", "03.54.30.64")
        val eeComponentBuildVersion0355 = Version("03.55.31", "03.55.30.42-1", "03.55.30.42")
        val eeComponentRCVersion0355 = Version("03.55.31", "03.55.30.53-1", "03.55.30.53")
        val eeComponentBuildVersion0356 = Version("03.56.31", "03.56.30.42-1", "03.56.30.56")
        val eeComponentNonExistsVersion0357 = Version("03.57.31", "03.57.30.64-1", "03.57.30.64")
        const val devReleaseNotesFileName = "release-notes-RC.txt"
        const val releaseReleaseNotesFileName = "release-notes-RELEASE.txt"
        val releaseNotesCoordinates =
            MavenArtifactCoordinatesDTO(GavDTO("test.upload", "release-notes", "1.0", "txt", "en"))
        val devMavenDistributionCoordinates =
            MavenArtifactCoordinatesDTO(GavDTO("test.add", "distribution", "1.0", "zip", "dev"))
        val releaseMavenDistributionCoordinates =
            MavenArtifactCoordinatesDTO(GavDTO("test.add", "distribution", "1.0", "zip", "release"))
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
        private fun releaseArtifacts(): Stream<Arguments> = Stream.of(
            Arguments.of(releaseMavenDistributionCoordinates),
            Arguments.of(releaseDebianDistributionCoordinates),
            Arguments.of(releaseRpmDistributionCoordinates)
        )
    }
    //</editor-fold>
}