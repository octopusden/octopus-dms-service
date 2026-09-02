package org.octopusden.octopus.dms.client.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for [Utils.readNonBlankContent].
 */
class UtilsTest {
    @Test
    fun `null path means nothing to parse`() {
        Assertions.assertNull(Utils.readNonBlankContent(null))
    }

    @Test
    fun `non-existent path means nothing to parse`(
        @TempDir directory: Path,
    ) {
        Assertions.assertNull(Utils.readNonBlankContent(directory.resolve("no-such-file.json").toFile()))
    }

    @Test
    fun `directory means nothing to parse`(
        @TempDir directory: Path,
    ) {
        Assertions.assertNull(Utils.readNonBlankContent(directory.toFile()))
    }

    @Test
    fun `empty file means nothing to parse`(
        @TempDir directory: Path,
    ) {
        val file = Files.createFile(directory.resolve("empty.json"))
        Assertions.assertNull(Utils.readNonBlankContent(file.toFile()))
    }

    @Test
    fun `newline-only file means nothing to parse`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("newline.json")
        Files.write(file, "\n".toByteArray())
        Assertions.assertNull(Utils.readNonBlankContent(file.toFile()))
    }

    @Test
    fun `whitespace-only file means nothing to parse`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("blanks.json")
        Files.write(file, " \t\r\n ".toByteArray())
        Assertions.assertNull(Utils.readNonBlankContent(file.toFile()))
    }

    @Test
    fun `content is returned verbatim for the caller to parse`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("config.json")
        val content = "{\"excludes\":[\"forbidden.xml\"]}"
        Files.write(file, content.toByteArray())
        Assertions.assertEquals(content, Utils.readNonBlankContent(file.toFile()))
    }

    @Test
    fun `utf-8 content survives the round trip`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("utf8.json")
        val content = "{\"excludes\":[\"förbidden.xml\"]}"
        Files.write(file, content.toByteArray(Charsets.UTF_8))
        Assertions.assertEquals(content, Utils.readNonBlankContent(file.toFile()))
    }

    @Test
    fun `malformed json content is still returned`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("broken.json")
        val content = "{\"excludes\": "
        Files.write(file, content.toByteArray())
        Assertions.assertEquals(content, Utils.readNonBlankContent(file.toFile()))
    }
}
