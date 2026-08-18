package org.octopusden.octopus.dms.client.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.escrow.dto.DistributionEntity
import org.octopusden.octopus.escrow.utilities.DistributionUtilities

/**
 * Documentation components take precedence over `artifacts.coordinates` for the artifacts they
 * distribute: such a coordinate takes its version from its documentation component, so the entry in
 * `artifacts.coordinates` is redundant and dropped. Everything the documentation components do not
 * distribute keeps going through the shared parameters.
 *
 * Which coordinates belong to a documentation component is stated by the Components Registry, so the
 * two sides are matched on data - on everything except the version - and never on a guess derived
 * from component names.
 */
class DocComponentPrecedenceTest {
    private fun entities(coordinates: String): Collection<DistributionEntity> = DistributionUtilities.parseDistributionGAV(coordinates)

    private fun docCoordinate(
        groupId: String,
        artifactId: String,
        version: String,
        packaging: String = "zip",
        classifier: String? = null,
    ) = MavenArtifactCoordinatesDTO(GavDTO(groupId, artifactId, version, packaging, classifier))

    @Test
    fun `a coordinate the documentation components cover is superseded`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english"),
            listOf(docCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english")),
        )
        Assertions.assertEquals(setOf("com.acme.doc:alpha-doc:zip:english"), superseded)
    }

    @Test
    fun `a coordinate the documentation components do not cover survives`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english,com.acme.other:unrelated:zip"),
            listOf(docCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english")),
        )
        Assertions.assertEquals(setOf("com.acme.doc:alpha-doc:zip:english"), superseded)
    }

    @Test
    fun `a different classifier is a different artifact and is not superseded`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:russian"),
            listOf(docCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english")),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }

    @Test
    fun `an omitted packaging means jar on both sides`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc"),
            listOf(docCoordinate("com.acme.doc", "alpha-doc", "1.0.32", packaging = "jar")),
        )
        Assertions.assertEquals(setOf("com.acme.doc:alpha-doc"), superseded)
    }

    /** A packaging mismatch fails loudly later rather than being treated as the same artifact. */
    @Test
    fun `an omitted packaging does not match a zip from the registry`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc"),
            listOf(docCoordinate("com.acme.doc", "alpha-doc", "1.0.32", packaging = "zip")),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }

    @Test
    fun `several documentation components supersede several coordinates`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english,com.acme.other.doc:beta-doc:zip"),
            listOf(
                docCoordinate("com.acme.doc", "alpha-doc", "1.0.32", classifier = "english"),
                docCoordinate("com.acme.other.doc", "beta-doc", "1.0.8"),
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
            listOf(docCoordinate("com.acme.doc", "alpha-doc", "1.0.32")),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }

    @Test
    fun `without documentation components nothing is superseded`() {
        val superseded = ArtifactServiceImpl.supersededMavenGavs(
            entities("com.acme.doc:alpha-doc:zip:english"),
            emptyList(),
        )
        Assertions.assertTrue(superseded.isEmpty(), superseded.toString())
    }
}
