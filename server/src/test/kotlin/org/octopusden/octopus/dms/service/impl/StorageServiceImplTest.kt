package org.octopusden.octopus.dms.service.impl

import org.apache.http.client.HttpResponseException
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ItemHandle
import org.jfrog.artifactory.client.RepositoryHandle
import org.jfrog.artifactory.client.model.File
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.configuration.StorageProperties
import org.octopusden.octopus.dms.exception.ArtifactStoreUnavailableException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import java.io.IOException

class StorageServiceImplTest {
    private val repository = "maven-release-repo"
    private val path = "com/example/artifact/1.0/artifact-1.0.jar"

    private val storageProperties = StorageProperties(
        mavenGroupPrefix = "com.example",
        artifactory = StorageProperties.ArtifactoryProperties(
            host = "https://artifactory.example.com",
            user = "user",
            password = "password",
            releaseRepositories = mapOf(RepositoryType.MAVEN to setOf(repository)),
        ),
    )

    private fun service(client: Artifactory) = StorageServiceImpl(storageProperties, client)

    private fun clientThrowing(failure: Throwable): Artifactory {
        val itemHandle = mock<ItemHandle>()
        // The real implementation throws HttpResponseException (a checked IOException) from a method
        // whose interface declares no `throws` clause, which Mockito's thenThrow() rejects outright
        // (it validates against the declared signature). thenAnswer bypasses that check and just runs.
        whenever(itemHandle.info<File>()).thenAnswer { throw failure }
        val repositoryHandle = mock<RepositoryHandle>()
        whenever(repositoryHandle.file(path)).thenReturn(itemHandle)
        val client = mock<Artifactory>()
        whenever(client.repository(repository)).thenReturn(repositoryHandle)
        return client
    }

    @Test
    fun `get throws UnableToFindArtifactException with an actionable message when every repository 404s`() {
        val client = clientThrowing(HttpResponseException(404, "Not Found"))

        val exception = assertThrows(UnableToFindArtifactException::class.java) {
            service(client).get(RepositoryType.MAVEN, false, path)
        }
        assertTrue(exception.message!!.contains(path))
        assertTrue(exception.message!!.contains(repository))
        assertTrue(exception.message!!.contains("published"))
        assertTrue(exception.message!!.contains("coordinates"))
    }

    @Test
    fun `get throws ArtifactStoreUnavailableException when a repository responds with a non-404 error`() {
        val client = clientThrowing(HttpResponseException(401, "Unauthorized"))

        val exception = assertThrows(ArtifactStoreUnavailableException::class.java) {
            service(client).get(RepositoryType.MAVEN, false, path)
        }
        assertTrue(exception.message!!.contains(repository))
        assertTrue(exception.message!!.contains(path))
        assertTrue(exception.message!!.contains("401"))
        assertTrue(exception.message!!.contains("does not confirm the artifact is missing"))
    }

    @Test
    fun `get throws ArtifactStoreUnavailableException when the repository lookup fails with a connection error`() {
        val client = clientThrowing(IOException("Connection refused"))

        val exception = assertThrows(ArtifactStoreUnavailableException::class.java) {
            service(client).get(RepositoryType.MAVEN, false, path)
        }
        assertTrue(exception.message!!.contains("Connection refused"))
    }
}
