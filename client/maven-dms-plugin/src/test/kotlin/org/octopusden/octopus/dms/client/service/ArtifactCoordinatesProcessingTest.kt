package org.octopusden.octopus.dms.client.service

import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.logging.SystemStreamLog
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import java.nio.file.Files
import java.nio.file.Path

/**
 * A coordinate passed through `artifacts.coordinates` may state the version it is published at as
 * `<coordinate>@<version>`; one that does not takes the version from `artifacts.coordinates.version`,
 * or from the released version when that is not set either. The version therefore belongs to the
 * coordinate rather than to the invocation, so artifacts released on different version lines can be
 * published together - which the single shared version cannot express.
 */
class ArtifactCoordinatesProcessingTest {
    private val service = ArtifactServiceImpl()

    private fun process(
        coordinates: String?,
        coordinatesVersion: String? = null,
        version: String = "1.2.3",
        type: String = "distribution",
    ): List<MavenArtifactCoordinatesDTO> =
        processAny(
            coordinates,
            coordinatesVersion,
            version,
            type,
        ).map { it as MavenArtifactCoordinatesDTO }

    private fun processAny(
        coordinates: String?,
        coordinatesVersion: String? = null,
        version: String = "1.2.3",
        type: String = "distribution",
    ): List<ArtifactCoordinatesDTO> {
        val collected = mutableListOf<ArtifactCoordinatesDTO>()
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
            1,
        ) { target -> collected.add(target.coordinates) }
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
        Assertions.assertTrue(exception.message!!.contains("is not a single version"), exception.message)
        Assertions.assertTrue(exception.message!!.contains("<coordinate>@<version>"), exception.message)
    }

    @Test
    fun `nothing to process is not a failure`() {
        Assertions.assertTrue(process(null).isEmpty())
    }

    @Test
    fun `a coordinate states the version it is published at`() {
        val resolved = process("com.acme:docs:zip:english@1.0.32")
        Assertions.assertEquals("1.0.32", resolved.single().gav.version)
        Assertions.assertEquals("docs", resolved.single().gav.artifactId)
        Assertions.assertEquals("english", resolved.single().gav.classifier)
    }

    @Test
    fun `the version of a coordinate wins over the shared one`() {
        val resolved = process("com.acme:docs:zip@1.0.32", coordinatesVersion = "9.9.9")
        Assertions.assertEquals("1.0.32", resolved.single().gav.version)
    }

    /**
     * The point of the suffix: documentation released on several version lines used to be
     * impossible to publish in one invocation, since the single shared version was applied to every
     * coordinate.
     */
    @Test
    fun `coordinates on different version lines are published together`() {
        val resolved = process(
            "com.acme:docs:zip:english@1.0.32,com.acme:docs:zip:spanish@1.0.8,com.acme:plain",
            coordinatesVersion = "2.0.0",
        ).associate { it.gav.classifier to it.gav.version }

        Assertions.assertEquals(mapOf("english" to "1.0.32", "spanish" to "1.0.8", null to "2.0.0"), resolved)
    }

    @Test
    fun `the same coordinate is published at each version it is listed with`() {
        val resolved = process("com.acme:docs:zip@1.0.32|com.acme:docs:zip@1.0.8")
            .map { it.gav.version }
            .sorted()
        Assertions.assertEquals(listOf("1.0.32", "1.0.8"), resolved)
    }

    /**
     * The shared version is rejected as a list only when a coordinate actually needs it, so an
     * invocation whose every coordinate states its own version is not held back by a stale value of
     * a parameter it does not use.
     */
    @Test
    fun `a list of versions is tolerated when no coordinate needs the shared version`() {
        val resolved = process("com.acme:docs:zip:english@1.0.32", coordinatesVersion = "1.0.32,1.0.8")
        Assertions.assertEquals("1.0.32", resolved.single().gav.version)
    }

    @Test
    fun `a coordinate with an empty version is rejected naming the entry`() {
        val exception = Assertions.assertThrows(MojoFailureException::class.java) {
            process("com.acme:docs:zip@")
        }
        Assertions.assertTrue(exception.message!!.contains("com.acme:docs:zip@"), exception.message)
    }

    /** A version is a single value, so a colon in it is a malformed entry rather than a coordinate. */
    @Test
    fun `a version that is not a single value is rejected`() {
        val exception = Assertions.assertThrows(MojoFailureException::class.java) {
            process("com.acme:docs:zip@1.0.32:extra")
        }
        Assertions.assertTrue(exception.message!!.contains("<coordinate>@<version>"), exception.message)
    }

    /**
     * An unparseable coordinate joins the other errors of the invocation rather than escaping as a
     * runtime exception: stripping the suffix off `com.acme@1.0` leaves a one-segment coordinate.
     */
    @Test
    fun `an unparseable coordinate is reported with the other errors`() {
        val exception = Assertions.assertThrows(MojoFailureException::class.java) {
            process("com.acme@1.0,com.acme:docs:zip@")
        }

        Assertions.assertTrue(exception.message!!.contains("Invalid GAV entry"), exception.message)
        Assertions.assertTrue(exception.message!!.contains("com.acme:docs:zip@"), exception.message)
    }

    /**
     * The one input the producing side is proven to emit for a doc component distributing both
     * forms: releng versions the coordinate and leaves the file URI alone.
     */
    @Test
    fun `a file uri and a versioned coordinate travel in one value`(
        @TempDir directory: Path,
    ) {
        val file = Files.createFile(directory.resolve("handbook.zip"))
        val resolved = processAny("${file.toUri()},org.acme.doc:docs:zip@1.5")
            .map { it as MavenArtifactCoordinatesDTO }
            .associate { it.gav.artifactId to it.gav.version }

        // The file artifact is published at the released version, the coordinate at its own.
        Assertions.assertEquals(mapOf("handbook" to "1.2.3", "docs" to "1.5"), resolved)
    }

    /** TeamCity parameters produce stray spaces routinely, so every part is trimmed. */
    @Test
    fun `spaces around the entry, the coordinate and the version are ignored`() {
        val resolved = process("  com.acme:docs:zip:english @ 1.0.32 ,  com.acme:plain  ", coordinatesVersion = "2.0.0")
            .associate { it.gav.artifactId to it.gav.version }

        Assertions.assertEquals(mapOf("docs" to "1.0.32", "plain" to "2.0.0"), resolved)
    }

    /**
     * `@` is a legitimate character in a path, so a file URI is taken verbatim - and a file artifact
     * is published at the released version rather than at one stated by the coordinate.
     */
    @Test
    fun `a file uri is not split on the version separator`(
        @TempDir directory: Path,
    ) {
        val file = Files.createFile(directory.resolve("docs@en.zip"))
        val resolved = processAny(file.toUri().toString()).single() as MavenArtifactCoordinatesDTO
        Assertions.assertEquals("docs@en", resolved.gav.artifactId)
        Assertions.assertEquals("zip", resolved.gav.packaging)
        Assertions.assertEquals("1.2.3", resolved.gav.version)
    }
}
