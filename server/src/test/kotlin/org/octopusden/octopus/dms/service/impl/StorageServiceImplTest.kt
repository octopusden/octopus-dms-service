package org.octopusden.octopus.dms.service.impl

import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ItemHandle
import org.jfrog.artifactory.client.RepositoryHandle
import org.jfrog.artifactory.client.model.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.configuration.StorageProperties
import org.octopusden.octopus.dms.exception.ArtifactStoreUnavailableException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import java.io.IOException

class StorageServiceImplTest {
    private val uploadRepository = "maven-upload"
    private val stagingRepository = "maven-staging"
    private val releaseRepository = "maven-release"
    private val coldRepository = "maven-cold"
    private val path = "com/example/artifact/1.0/artifact-1.0.jar"
    private val dockerPath = "example/image/1.0"

    private val storageProperties = StorageProperties(
        mavenGroupPrefix = "com.example",
        artifactory = StorageProperties.ArtifactoryProperties(
            host = "https://artifactory.example.com",
            user = "user",
            password = "password",
            uploadRepositories = mapOf(RepositoryType.MAVEN to uploadRepository),
            stagingRepositories = mapOf(RepositoryType.MAVEN to setOf(stagingRepository)),
            releaseRepositories = mapOf(RepositoryType.MAVEN to setOf(releaseRepository)),
            coldRepositories = mapOf(RepositoryType.MAVEN to setOf(coldRepository)),
        ),
    )

    private val notFound: () -> File = { throw HttpResponseException(404, "Not Found") }

    private fun artifactory(
        queriedPath: String = path,
        outcomes: Map<String, () -> File>,
    ): Artifactory {
        val client = mock<Artifactory>()
        outcomes.forEach { (repository, outcome) ->
            val itemHandle = mock<ItemHandle>()
            whenever(itemHandle.info<File>()).thenAnswer { outcome() }
            val repositoryHandle = mock<RepositoryHandle>()
            whenever(repositoryHandle.file(queriedPath)).thenReturn(itemHandle)
            whenever(client.repository(repository)).thenReturn(repositoryHandle)
        }
        return client
    }

    private fun service(client: Artifactory) = StorageServiceImpl(storageProperties, client)

    @Test
    fun `find returns the first repository's hit without querying the ones after it`() {
        val expected = mock<File>()
        val client = artifactory(
            outcomes = mapOf(
                uploadRepository to { expected },
                stagingRepository to notFound,
                releaseRepository to notFound,
                coldRepository to notFound,
            ),
        )

        assertSame(expected, service(client).find(RepositoryType.MAVEN, true, path))
        verify(client, never()).repository(stagingRepository)
        verify(client, never()).repository(releaseRepository)
        verify(client, never()).repository(coldRepository)
    }

    @Test
    fun `find walks past a repository that 404s and returns a later repository's hit`() {
        val expected = mock<File>()
        val client = artifactory(
            outcomes = mapOf(
                uploadRepository to notFound,
                stagingRepository to notFound,
                releaseRepository to { expected },
                coldRepository to notFound,
            ),
        )

        assertSame(expected, service(client).find(RepositoryType.MAVEN, true, path))
        verify(client, never()).repository(coldRepository)
    }

    @Test
    fun `find returns null rather than throwing when every repository 404s`() {
        val client = artifactory(
            outcomes = mapOf(
                uploadRepository to notFound,
                stagingRepository to notFound,
                releaseRepository to notFound,
                coldRepository to notFound,
            ),
        )

        assertNull(service(client).find(RepositoryType.MAVEN, true, path))
    }

    @Test
    fun `find skips the staging repositories when staging is excluded`() {
        val expected = mock<File>()
        val client = artifactory(
            outcomes = mapOf(
                uploadRepository to notFound,
                releaseRepository to { expected },
            ),
        )

        assertSame(expected, service(client).find(RepositoryType.MAVEN, false, path))
        verify(client, never()).repository(stagingRepository)
    }

    @Test
    fun `find looks up a Docker artifact by its manifest`() {
        val expected = mock<File>()
        val client = artifactory(
            queriedPath = "$dockerPath/manifest.json",
            outcomes = mapOf(uploadRepository to { expected }),
        )
        val properties = storageProperties.copy(
            artifactory = storageProperties.artifactory.copy(
                uploadRepositories = mapOf(RepositoryType.DOCKER to uploadRepository),
                stagingRepositories = emptyMap(),
                releaseRepositories = emptyMap(),
                coldRepositories = emptyMap(),
            ),
        )

        assertSame(expected, StorageServiceImpl(properties, client).find(RepositoryType.DOCKER, false, dockerPath))
    }

    @Test
    fun `get returns the found artifact`() {
        val expected = mock<File>()
        val client = artifactory(outcomes = mapOf(uploadRepository to { expected }))

        assertSame(expected, service(client).get(RepositoryType.MAVEN, true, path))
    }

    @Test
    fun `get throws UnableToFindArtifactException naming every repository checked when all of them 404`() {
        val client = artifactory(
            outcomes = mapOf(
                uploadRepository to notFound,
                stagingRepository to notFound,
                releaseRepository to notFound,
                coldRepository to notFound,
            ),
        )

        val exception = assertThrows(UnableToFindArtifactException::class.java) {
            service(client).get(RepositoryType.MAVEN, true, path)
        }
        assertEquals("DMS-40006", exception.code)
        assertTrue(exception.message!!.contains(path))
        listOf(uploadRepository, stagingRepository, releaseRepository, coldRepository).forEach {
            assertTrue(exception.message!!.contains(it), "Expected '$it' in: ${exception.message}")
        }
        assertTrue(exception.message!!.contains("published"))
        assertTrue(exception.message!!.contains("coordinates"))
    }

    @Test
    fun `get's not-found message stays neutral about which caller asked, since download uses it too`() {
        val client = artifactory(outcomes = mapOf(uploadRepository to notFound))
        val properties = storageProperties.copy(
            artifactory = storageProperties.artifactory.copy(
                stagingRepositories = emptyMap(),
                releaseRepositories = emptyMap(),
                coldRepositories = emptyMap(),
            ),
        )

        val exception = assertThrows(UnableToFindArtifactException::class.java) {
            StorageServiceImpl(properties, client).get(RepositoryType.MAVEN, false, path)
        }
        assertFalse(exception.message!!.contains("validation"))
    }

    @Test
    fun `a non-404 response aborts the walk instead of falling through to the next repository`() {
        val client = artifactory(
            outcomes = mapOf(
                uploadRepository to { throw HttpResponseException(500, "Internal Server Error") },
                stagingRepository to notFound,
            ),
        )

        val exception = assertThrows(ArtifactStoreUnavailableException::class.java) {
            service(client).find(RepositoryType.MAVEN, true, path)
        }
        assertEquals("DMS-40015", exception.code)
        assertTrue(exception.message!!.contains(uploadRepository))
        assertTrue(exception.message!!.contains(path))
        assertTrue(exception.message!!.contains("500"))
        assertTrue(exception.message!!.contains("could not be queried"))
        verify(client, never()).repository(stagingRepository)
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403, 407])
    fun `a rejected-credentials status reads as a configuration problem, not a transient one`(status: Int) {
        val client = artifactory(outcomes = mapOf(uploadRepository to { throw HttpResponseException(status, "Denied") }))

        val exception = assertThrows(ArtifactStoreUnavailableException::class.java) {
            service(client).get(RepositoryType.MAVEN, true, path)
        }
        assertTrue(exception.message!!.contains("credentials"))
        assertTrue(exception.message!!.contains("retrying will not help"))
        assertFalse(exception.message!!.contains("could not be queried"))
    }

    @Test
    fun `a connection failure is reported as ArtifactStoreUnavailableException, not propagated raw`() {
        val client = artifactory(outcomes = mapOf(uploadRepository to { throw IOException("Connection refused") }))

        val exception = assertThrows(ArtifactStoreUnavailableException::class.java) {
            service(client).get(RepositoryType.MAVEN, true, path)
        }
        assertTrue(exception.message!!.contains("Connection refused"))
        assertTrue(exception.message!!.contains("could not be queried"))
    }

    @Test
    fun `the underlying failure is kept as the cause so the server log retains its stack trace`() {
        val cause = IOException("Connection refused")
        val client = artifactory(outcomes = mapOf(uploadRepository to { throw cause }))

        val exception = assertThrows(ArtifactStoreUnavailableException::class.java) {
            service(client).get(RepositoryType.MAVEN, true, path)
        }
        assertSame(cause, exception.cause)
    }

    @Test
    fun `a programming error is not masked as ArtifactStoreUnavailableException`() {
        val client = artifactory(outcomes = mapOf(uploadRepository to { throw NullPointerException("boom") }))

        assertThrows(NullPointerException::class.java) {
            service(client).get(RepositoryType.MAVEN, true, path)
        }
    }
}
