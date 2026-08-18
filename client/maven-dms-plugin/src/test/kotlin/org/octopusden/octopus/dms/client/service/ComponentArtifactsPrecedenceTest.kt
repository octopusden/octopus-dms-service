package org.octopusden.octopus.dms.client.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.escrow.dto.DistributionEntity
import org.octopusden.octopus.escrow.utilities.DistributionUtilities

/**
 * `artifacts.components` takes precedence over `artifacts.coordinates` for the artifacts it covers:
 * such a coordinate takes its version from the component it belongs to, so the entry in
 * `artifacts.coordinates` is redundant and dropped. Everything not covered keeps going through the
 * shared parameters.
 *
 * Which coordinates belong to a component version is stated by the Components Registry, so the two
 * sides are matched on data - on everything except the version - and never on a guess derived from
 * component names.
 */
class ComponentArtifactsPrecedenceTest {
    private fun entities(coordinates: String): Collection<DistributionEntity> = DistributionUtilities.parseDistributionGAV(coordinates)

    private fun registryCoordinate(
        groupId: String,
        artifactId: String,
        version: String,
        packaging: String = "zip",
        classifier: String? = null,
    ) = MavenArtifactCoordinatesDTO(GavDTO(groupId, artifactId, version, packaging, classifier))

    @Test
    fun `a covered coordinate is superseded`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english"),
            listOf(registryCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english")),
        )
        Assertions.assertEquals(setOf("com.acme.doc:alpha-doc:zip:english"), superseded)
    }

    @Test
    fun `an uncovered coordinate survives`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english,com.acme.other:unrelated:zip"),
            listOf(registryCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english")),
        )
        Assertions.assertEquals(setOf("com.acme.doc:alpha-doc:zip:english"), superseded)
    }

    @Test
    fun `a different classifier is a different artifact and is not superseded`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:russian"),
            listOf(registryCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english")),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }

    @Test
    fun `an omitted packaging means jar on both sides`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc"),
            listOf(registryCoordinate("com.acme.doc", "alpha-doc", "1.0.32", packaging = "jar")),
        )
        Assertions.assertEquals(setOf("com.acme.doc:alpha-doc"), superseded)
    }

    /** A packaging mismatch fails loudly later rather than being treated as the same artifact. */
    @Test
    fun `an omitted packaging does not match a zip from the registry`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc"),
            listOf(registryCoordinate("com.acme.doc", "alpha-doc", "1.0.32", packaging = "zip")),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }

    @Test
    fun `several components supersede several coordinates`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english,com.acme.other.doc:beta-doc:zip"),
            listOf(
                registryCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english"),
                registryCoordinate("com.acme.other.doc", "beta-doc", "1.0.8"),
            ),
        )
        Assertions.assertEquals(
            setOf("com.acme.doc:alpha-doc:zip:english", "com.acme.other.doc:beta-doc:zip"),
            superseded,
        )
    }

    @Test
    fun `file entries are never superseded`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("file:///opt/dist/alpha-doc.zip"),
            listOf(registryCoordinate("com.acme.doc", "alpha-doc", "1.0.32")),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }

    @Test
    fun `without resolved components nothing is superseded`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english"),
            emptyList(),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }
}
