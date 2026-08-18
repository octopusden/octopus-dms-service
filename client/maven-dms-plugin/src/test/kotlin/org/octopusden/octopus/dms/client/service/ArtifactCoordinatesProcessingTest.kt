package org.octopusden.octopus.dms.client.service

import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.logging.SystemStreamLog
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO

/**
 * Coordinates passed through `artifacts.coordinates` are version agnostic: the version comes from
 * `artifacts.coordinates.version`, or from the released version when that is not set. This is the
 * long standing behaviour and it must stay untouched - components released on their own version
 * lines are handled by `artifacts.components` instead.
 */
class ArtifactCoordinatesProcessingTest {
    private val service = ArtifactServiceImpl()

    private fun process(
        coordinates: String?,
        coordinatesVersion: String? = null,
        version: String = "1.2.3",
        artifactsComponents: String? = null,
        cregUrl: String? = null,
        type: String = "distribution",
    ): List<MavenArtifactCoordinatesDTO> {
        val collected = mutableListOf<MavenArtifactCoordinatesDTO>()
        service.processArtifacts(
            SystemStreamLog(),
            "com.acme.distribution",
            "ee-component",
            version,
            null,
            type,
            null,
            coordinates,
            coordinatesVersion,
            null,
            null,
            null,
            artifactsComponents,
            cregUrl,
            1,
        ) { target -> collected.add(target.coordinates as MavenArtifactCoordinatesDTO) }
        return collected
    }

    @Test
    fun `two to four part coordinates take the version from the version parameter`() {
        val resolved = process(
            "com.acme:plain,com.acme:with-packaging:zip,com.acme:with-classifier:zip:english",
            coordinatesVersion = "1.0.32",
        ).sortedBy { it.gav.artifactId }

        Assertions.assertEquals(3, resolved.size)
        Assertions.assertTrue(resolved.all { it.gav.version == "1.0.32" })
        with(resolved[0].gav) {
            Assertions.assertEquals("plain", artifactId)
            Assertions.assertEquals("jar", packaging)
            Assertions.assertNull(classifier)
        }
        with(resolved[1].gav) {
            Assertions.assertEquals("with-classifier", artifactId)
            Assertions.assertEquals("zip", packaging)
            Assertions.assertEquals("english", classifier)
        }
        with(resolved[2].gav) {
            Assertions.assertEquals("with-packaging", artifactId)
            Assertions.assertEquals("zip", packaging)
            Assertions.assertNull(classifier)
        }
    }

    @Test
    fun `without a version parameter the released version is used`() {
        val resolved = process("com.acme:docs:zip")
        Assertions.assertEquals("1.2.3", resolved.single().gav.version)
    }

    /**
     * Guards the failure this whole change came from: several documentation versions used to be
     * joined into the single version parameter and applied verbatim to every coordinate, producing
     * a request for `.../1.0.32,1.0.8/...` that could only ever 404.
     */
    @Test
    fun `a list of versions in the version parameter is rejected with an actionable message`() {
        val exception = Assertions.assertThrows(MojoFailureException::class.java) {
            process("com.acme:docs:zip:english", coordinatesVersion = "1.0.32,1.0.8")
        }
        Assertions.assertTrue(exception.message!!.contains("looks like a list of versions"), exception.message)
        Assertions.assertTrue(exception.message!!.contains("artifacts.components"), exception.message)
    }

    @Test
    fun `components without a registry url are rejected`() {
        val exception = Assertions.assertThrows(MojoFailureException::class.java) {
            process(null, artifactsComponents = "alpha:1.0.32")
        }
        Assertions.assertTrue(exception.message!!.contains("creg.url"), exception.message)
    }

    @Test
    fun `nothing to process is not a failure`() {
        Assertions.assertTrue(process(null).isEmpty())
    }
}
