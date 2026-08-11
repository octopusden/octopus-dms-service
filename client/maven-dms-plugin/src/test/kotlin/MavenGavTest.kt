import org.apache.maven.plugin.MojoFailureException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.dms.client.util.Utils

/**
 * Artifact coordinates are `groupId:artifactId[:packaging[:classifier[:version]]]`.
 *
 * The version is optional and, when omitted, comes from the single `artifactsCoordinatesVersion`
 * parameter shared by every coordinate of an invocation. Spelling it out per coordinate is what
 * allows artifacts released on different version lines to be uploaded together.
 */
class MavenGavTest {

    private val defaultVersion = "2.0.0"

    @Test
    fun `group and artifact only fall back to jar and the shared version`() {
        val gav = Utils.parseMavenGav("com.acme:docs", defaultVersion)
        Assertions.assertEquals("com.acme", gav.groupId)
        Assertions.assertEquals("docs", gav.artifactId)
        Assertions.assertEquals(defaultVersion, gav.version)
        Assertions.assertEquals("jar", gav.packaging)
        Assertions.assertNull(gav.classifier)
    }

    @Test
    fun `packaging is taken from the coordinate`() {
        val gav = Utils.parseMavenGav("com.acme:docs:zip", defaultVersion)
        Assertions.assertEquals("zip", gav.packaging)
        Assertions.assertNull(gav.classifier)
        Assertions.assertEquals(defaultVersion, gav.version)
    }

    @Test
    fun `classifier is taken from the coordinate`() {
        val gav = Utils.parseMavenGav("com.acme:docs:zip:english", defaultVersion)
        Assertions.assertEquals("zip", gav.packaging)
        Assertions.assertEquals("english", gav.classifier)
        Assertions.assertEquals(defaultVersion, gav.version)
    }

    @Test
    fun `version in the coordinate wins over the shared version`() {
        val gav = Utils.parseMavenGav("com.acme:docs:zip:english:1.0.32", defaultVersion)
        Assertions.assertEquals("com.acme", gav.groupId)
        Assertions.assertEquals("docs", gav.artifactId)
        Assertions.assertEquals("1.0.32", gav.version)
        Assertions.assertEquals("zip", gav.packaging)
        Assertions.assertEquals("english", gav.classifier)
    }

    @Test
    fun `version can be given without a classifier`() {
        val gav = Utils.parseMavenGav("com.acme:docs:zip::1.0.8", defaultVersion)
        Assertions.assertEquals("1.0.8", gav.version)
        Assertions.assertEquals("zip", gav.packaging)
        Assertions.assertNull(gav.classifier)
    }

    @Test
    fun `artifacts released on different version lines keep their own versions`() {
        val first = Utils.parseMavenGav("com.acme.doc.cw:consumer-wallet-documentation:zip:english:1.0.32", null)
        val second = Utils.parseMavenGav("com.acme.test.doc:test-component-doc:zip::1.0.8", null)
        Assertions.assertEquals("1.0.32", first.version)
        Assertions.assertEquals("1.0.8", second.version)
    }

    @Test
    fun `a coordinate without a version and without a shared version is rejected`() {
        Assertions.assertThrows(MojoFailureException::class.java) {
            Utils.parseMavenGav("com.acme:docs:zip", null)
        }
    }

    @Test
    fun `too few or too many parts are rejected`() {
        listOf("com.acme", "com.acme:docs:zip:english:1.0.32:extra").forEach {
            Assertions.assertThrows(MojoFailureException::class.java, { Utils.parseMavenGav(it, defaultVersion) }, it)
        }
    }

    @Test
    fun `an empty group artifact packaging or version is rejected`() {
        listOf(
            ":docs:zip",
            "com.acme::zip",
            "com.acme:docs::english:1.0.32",
            "com.acme:docs:zip:english:"
        ).forEach {
            Assertions.assertThrows(MojoFailureException::class.java, { Utils.parseMavenGav(it, defaultVersion) }, it)
        }
    }

    @Test
    fun `validation accepts two to five parts and rejects anything else`() {
        listOf(
            "com.acme:docs",
            "com.acme:docs:zip",
            "com.acme:docs:zip:english",
            "com.acme:docs:zip:english:1.0.32",
            "com.acme:docs:zip::1.0.8"
        ).forEach { Assertions.assertTrue(Utils.isValidMavenGav(it), it) }

        listOf(
            "com.acme",
            "com.acme:docs:zip:english:1.0.32:extra",
            "com.acme:docs:1.0.32,1.0.8",
            "com.acme docs:zip",
            ""
        ).forEach { Assertions.assertFalse(Utils.isValidMavenGav(it), it) }
    }
}
