package org.octopusden.octopus.dms.client.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.octopusden.octopus.util.FileFilterConfig
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for [Utils.readJsonIfNotBlank].
 */
class UtilsTest {
    private val objectMapper = ObjectMapper()

    private fun parse(file: java.io.File?) = Utils.readJsonIfNotBlank(file, objectMapper, FileFilterConfig::class.java)

    @Test
    fun `null path means nothing to parse`() {
        Assertions.assertNull(parse(null))
    }

    @Test
    fun `non-existent path means nothing to parse`(
        @TempDir directory: Path,
    ) {
        Assertions.assertNull(parse(directory.resolve("no-such-file.json").toFile()))
    }

    @Test
    fun `directory means nothing to parse`(
        @TempDir directory: Path,
    ) {
        Assertions.assertNull(parse(directory.toFile()))
    }

    @Test
    fun `empty file means nothing to parse`(
        @TempDir directory: Path,
    ) {
        val file = Files.createFile(directory.resolve("empty.json"))
        Assertions.assertNull(parse(file.toFile()))
    }

    @Test
    fun `whitespace-only file means nothing to parse`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("blanks.json")
        Files.write(file, " \t\r\n ".toByteArray())
        Assertions.assertNull(parse(file.toFile()))
    }

    @Test
    fun `bom-only file means nothing to parse`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("bom.json")
        Files.write(file, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        Assertions.assertNull(parse(file.toFile()))
    }

    @Test
    fun `valid json parses into a filter config`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("wlignore.json")
        Files.write(file, "{\"excludeFiles\":[\"forbidden.xml\"]}".toByteArray())
        Assertions.assertEquals(listOf("forbidden.xml"), parse(file.toFile()).excludeFiles)
    }

    @Test
    fun `valid json with a utf-8 bom parses`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("bom-wlignore.json")
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "{\"excludeFiles\":[\"forbidden.xml\"]}".toByteArray()
        Files.write(file, bytes)
        Assertions.assertEquals(listOf("forbidden.xml"), parse(file.toFile()).excludeFiles)
    }

    @Test
    fun `valid json in utf-16 parses`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("utf16-wlignore.json")
        Files.write(file, "{\"excludeFiles\":[\"forbidden.xml\"]}".toByteArray(StandardCharsets.UTF_16))
        Assertions.assertEquals(listOf("forbidden.xml"), parse(file.toFile()).excludeFiles)
    }

    @Test
    fun `malformed json surfaces the parser failure`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("broken.json")
        Files.write(file, "{\"excludeFiles\": ".toByteArray())
        Assertions.assertThrows(IOException::class.java) { parse(file.toFile()) }
    }
}
