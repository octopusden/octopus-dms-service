import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.components.registry.core.dto.DistributionDTO
import org.octopusden.octopus.dms.client.service.ComponentArtifactsResolver

/**
 * A pair is `<component>:<version>`, so every component carries its own version. The coordinates are
 * not part of the pair - they come from that component version's `distribution.GAV` in the Components
 * Registry, which is what lets an artifact be added to a component without any change on the
 * consumer side.
 */
class ComponentArtifactsResolverTest {
    private fun resolver(vararg distributions: Pair<String, String?>): ComponentArtifactsResolver {
        val byKey = distributions.toMap()
        return ComponentArtifactsResolver { component, version ->
            val gav = byKey["$component:$version"]
                ?: error("Component '$component' version '$version' is not found")
            DistributionDTO(false, true, gav)
        }
    }

    @Test
    fun `coordinates come from the registry and the version from the link`() {
        val errors = mutableListOf<String>()
        val resolved = resolver(
            "doc-alpha:1.0.32" to "com.acme.doc:alpha-doc:zip:english",
        ).resolve("doc-alpha:1.0.32", errors)

        Assertions.assertEquals(emptyList<String>(), errors)
        Assertions.assertEquals(1, resolved.size)
        with(resolved[0].gav) {
            Assertions.assertEquals("com.acme.doc", groupId)
            Assertions.assertEquals("alpha-doc", artifactId)
            Assertions.assertEquals("1.0.32", version)
            Assertions.assertEquals("zip", packaging)
            Assertions.assertEquals("english", classifier)
        }
    }

    /** The case that used to fail: two components on different version lines. */
    @Test
    fun `each component keeps its own version`() {
        val errors = mutableListOf<String>()
        val resolved = resolver(
            "doc-alpha:1.0.32" to "com.acme.doc:alpha-doc:zip:english",
            "doc-beta:1.0.8" to "com.acme.other.doc:beta-doc:zip",
        ).resolve("doc-alpha:1.0.32,doc-beta:1.0.8", errors)

        Assertions.assertEquals(emptyList<String>(), errors)
        Assertions.assertEquals(
            listOf(
                "com/acme/doc/alpha-doc/1.0.32/alpha-doc-1.0.32-english.zip",
                "com/acme/other/doc/beta-doc/1.0.8/beta-doc-1.0.8.zip",
            ),
            resolved.map { it.toPath() },
        )
    }

    @Test
    fun `a component distributing several artifacts yields one coordinate per artifact`() {
        val errors = mutableListOf<String>()
        val resolved = resolver(
            "doc-alpha:3.2.15" to "com.acme.doc:alpha-doc:zip:english,com.acme.doc:alpha-doc:zip:russian",
        ).resolve("doc-alpha:3.2.15", errors)

        Assertions.assertEquals(emptyList<String>(), errors)
        Assertions.assertEquals(listOf("english", "russian"), resolved.map { it.gav.classifier })
        Assertions.assertTrue(resolved.all { it.gav.version == "3.2.15" })
    }

    @Test
    fun `packaging defaults to jar and classifier stays absent`() {
        val errors = mutableListOf<String>()
        val resolved = resolver("doc-alpha:1.0" to "com.acme.doc:alpha-doc").resolve("doc-alpha:1.0", errors)

        Assertions.assertEquals(emptyList<String>(), errors)
        Assertions.assertEquals("jar", resolved[0].gav.packaging)
        Assertions.assertNull(resolved[0].gav.classifier)
    }

    @Test
    fun `the same component listed twice is resolved once`() {
        val errors = mutableListOf<String>()
        val resolved = resolver("doc-alpha:1.0" to "com.acme.doc:alpha-doc:zip")
            .resolve("doc-alpha:1.0,doc-alpha:1.0", errors)

        Assertions.assertEquals(emptyList<String>(), errors)
        Assertions.assertEquals(1, resolved.size)
    }

    @Test
    fun `blank input resolves to nothing without errors`() {
        val errors = mutableListOf<String>()
        Assertions.assertTrue(resolver().resolve("", errors).isEmpty())
        Assertions.assertTrue(resolver().resolve(null, errors).isEmpty())
        Assertions.assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `a malformed link is reported and does not stop the other links`() {
        val errors = mutableListOf<String>()
        val resolved = resolver("doc-alpha:1.0" to "com.acme.doc:alpha-doc:zip")
            .resolve("doc-alpha:1.0,no-version,:1.0,doc-beta:", errors)

        Assertions.assertEquals(1, resolved.size)
        Assertions.assertEquals(3, errors.size)
        Assertions.assertTrue(errors.all { it.contains("<component>:<version>") }, errors.toString())
    }

    @Test
    fun `a component without a distribution GAV is reported`() {
        val errors = mutableListOf<String>()
        val resolved = ComponentArtifactsResolver { _, _ -> DistributionDTO(false, true, null) }
            .resolve("doc-alpha:1.0", errors)

        Assertions.assertTrue(resolved.isEmpty())
        Assertions.assertEquals(1, errors.size)
        Assertions.assertTrue(errors[0].contains("no distribution GAV"), errors[0])
    }

    @Test
    fun `an unknown component is reported instead of failing the resolution`() {
        val errors = mutableListOf<String>()
        val resolved = resolver("doc-alpha:1.0" to "com.acme.doc:alpha-doc:zip")
            .resolve("doc-alpha:1.0,doc-missing:9.9", errors)

        Assertions.assertEquals(1, resolved.size)
        Assertions.assertEquals(1, errors.size)
        Assertions.assertTrue(errors[0].contains("doc-missing"), errors[0])
    }

    @Test
    fun `a non MAVEN distribution entity is reported`() {
        val errors = mutableListOf<String>()
        val resolved = resolver("doc-alpha:1.0" to "file:///opt/dist/alpha-doc.zip").resolve("doc-alpha:1.0", errors)

        Assertions.assertTrue(resolved.isEmpty())
        Assertions.assertEquals(1, errors.size)
        Assertions.assertTrue(errors[0].contains("non MAVEN entity"), errors[0])
    }
}
