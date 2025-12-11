package org.octopusden.octopus.dms.client.validation

import org.octopusden.octopus.dms.client.common.dto.ContentValidatorPropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.LicenseValidatorPropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.NameValidatorPropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.maven.plugin.logging.Log
import org.octopusden.octopus.components.automation.task.Properties
import org.octopusden.octopus.tools.wl.validation.validator.CopyrightValidator
import org.redline_rpm.ReadableChannelWrapper
import org.redline_rpm.Scanner
import org.redline_rpm.Util
import org.springframework.util.AntPathMatcher

class ArtifactValidator private constructor(
    validationProperties: ValidationPropertiesDTO,
    private val log: Log
) {
    private class LicenseValidator(
        val licenseValidatorProperties: LicenseValidatorPropertiesDTO
    ) {
        fun validate(path: String, file: Path) =
            if (licenseValidatorProperties.enabled &&
                file.inputStream().use { detectFileType(BufferedInputStream(it)) } == FileType.ZIP &&
                !ZipFile(file.toFile()).stream()
                    .filter { entry -> !entry.isDirectory && licenseValidatorProperties.pattern.matches(entry.name) }
                    .map { entry -> entry.name }
                    .findAny().isPresent
            ) {
                listOf("$path: third party license file does not found")
            } else {
                emptyList()
            }
    }

    private class NameValidator(
        val nameValidatorProperties: NameValidatorPropertiesDTO
    ) {
        fun validate(path: String): List<String> {
            if (nameValidatorProperties.enabled) {
                val elements = path.split('/').filter {
                    it.isNotEmpty() && !nameValidatorProperties.allowedPattern.matches(it)
                }
                if (elements.isNotEmpty()) {
                    return listOf("$path: elements $elements does not match regexp '${nameValidatorProperties.allowedPattern.pattern}'")
                }
            }
            return emptyList()
        }
    }

    private class ContentValidator(
        val contentValidatorProperties: ContentValidatorPropertiesDTO
    ) {
        val copyrightValidator = CopyrightValidator(
            properties = Properties(
                contains = contentValidatorProperties.forbiddenTokens,
                patterns = contentValidatorProperties.forbiddenPatterns,
                exceptions = emptyList(), //IMPORTANT: not used by CopyrightValidator at the moment
                restricted = ""           //IMPORTANT: not used by CopyrightValidator at the moment
            ),
            threadCount = contentValidatorProperties.parallelism
        )
        val pathMatcher = AntPathMatcher()
        val exclude = contentValidatorProperties.exclude.map { "**/$it/**" }.asSequence()
        val tempFile = createTempFile().apply { this.toFile().deleteOnExit() }

        fun validate(path: String, file: InputStream, log: Log) =
            if (contentValidatorProperties.enabled) {
                if (exclude.any { pathMatcher.match(it, path) }) {
                    log.debug("File $path is skipped by content validator")
                    emptyList()
                } else {
                    tempFile.outputStream().use { file.copyTo(it) }
                    //IMPORTANT: CopyrightValidator.validate() seems to produce memory leak
                    //IMPORTANT: CopyrightValidator.validate() closes input stream therefore usage of temporary file is required
                    //IMPORTANT: explicitly close resource in case CopyrightValidator.validate() changes its behaviour in new version
                    tempFile.inputStream().use {
                        copyrightValidator.validate(it).map {
                            "$path: line ${it.line}, token '${it.problemToken}' matches regexp '${it.brokenRegex}'"
                        }
                    }
                }
            } else {
                emptyList()
            }

    }

    private val licenseValidator = LicenseValidator(validationProperties.licenseValidation)
    private val nameValidator = NameValidator(validationProperties.nameValidation)
    private val contentValidator = ContentValidator(validationProperties.contentValidation)

    private fun validateFile(path: String, file: BufferedInputStream): List<String> {
        val type = detectFileType(file)
        log.debug("Validate $type file $path")
        try {
            return nameValidator.validate(path) +
                    when (type) {
                        FileType.PLAIN -> contentValidator.validate(path, file, log)
                        FileType.ZIP -> {
                            val zipFile = ZipArchiveInputStream(file)
                            val errors = ArrayList<String>()
                            var entry = zipFile.nextZipEntry
                            while (entry != null) {
                                val entryPath = "$path/${entry.name}"
                                if (entry.isDirectory || entry.isUnixSymlink) {
                                    log.debug("Validate $entryPath")
                                    errors.addAll(nameValidator.validate(entryPath))
                                } else {
                                    errors.addAll(validateFile(entryPath, BufferedInputStream(zipFile, BUFFER_SIZE)))
                                }
                                entry = zipFile.nextZipEntry
                            }
                            errors
                        }

                        FileType.AR -> {
                            val arFile = ArArchiveInputStream(file)
                            val errors = ArrayList<String>()
                            var entry = arFile.nextArEntry
                            while (entry != null) {
                                val entryPath = "$path/${entry.name}"
                                errors.addAll(validateFile(entryPath, BufferedInputStream(arFile, BUFFER_SIZE)))
                                entry = arFile.nextArEntry
                            }
                            errors
                        }

                        FileType.TAR, FileType.TARGZ, FileType.TARXZ -> {
                            val tarFile =
                                if (type == FileType.TARXZ) TarArchiveInputStream(XZCompressorInputStream(file))
                                else if (type == FileType.TARGZ) TarArchiveInputStream(GZIPInputStream(file))
                                else TarArchiveInputStream(file)
                            val errors = ArrayList<String>()
                            var entry = tarFile.nextTarEntry
                            while (entry != null) {
                                val entryPath = "$path/${entry.name}"
                                if (entry.isFile) {
                                    errors.addAll(validateFile(entryPath, BufferedInputStream(tarFile, BUFFER_SIZE)))
                                } else {
                                    log.debug("Validate $entryPath")
                                    errors.addAll(nameValidator.validate(entryPath))
                                }
                                entry = tarFile.nextTarEntry
                            }
                            errors

                        }

                        FileType.RPM -> Scanner().run(ReadableChannelWrapper(Channels.newChannel(file))).header.run {
                            val errors = ArrayList<String>()
                            val payload = CpioArchiveInputStream(Util.openPayloadStream(this, file))
                            var entry = payload.nextCPIOEntry
                            while (entry != null) {
                                val entryPath = "$path/${entry.name}"
                                if (entry.isRegularFile) {
                                    errors.addAll(validateFile(entryPath, BufferedInputStream(payload, BUFFER_SIZE)))
                                } else {
                                    log.debug("Validate $entryPath")
                                    errors.addAll(nameValidator.validate(entryPath))
                                }
                                entry = payload.nextCPIOEntry
                            }
                            errors
                        }
                    }
        } catch (e: Exception) {
            log.warn("$path: validation exception", e)
            return listOf("$path: validation exception '${e.message}'")
        }
    }

    private fun validate(name: String, file: Path) = licenseValidator.validate(name, file) +
            file.inputStream().use { validateFile(name, BufferedInputStream(it, BUFFER_SIZE)) }

    companion object {
        private const val BUFFER_SIZE = 524288

        private enum class FileType {
            PLAIN, ZIP, AR, TAR, TARGZ, TARXZ, RPM
        }

        private val detectFunctions = sequenceOf(
            { file: InputStream ->
                Scanner().run(ReadableChannelWrapper(Channels.newChannel(file))).header.apply {
                    CpioArchiveInputStream(Util.openPayloadStream(this, file)).nextCPIOEntry
                }
                FileType.RPM
            },
            { file: InputStream ->
                when (ArchiveStreamFactory.detect(BufferedInputStream(XZCompressorInputStream(file)))) {
                    ArchiveStreamFactory.TAR -> FileType.TARXZ
                    else -> {
                        FileType.PLAIN
                    }
                }
            },
            { file: InputStream ->
                when (ArchiveStreamFactory.detect(BufferedInputStream(GZIPInputStream(file)))) {
                    ArchiveStreamFactory.TAR -> FileType.TARGZ
                    else -> {
                        FileType.PLAIN
                    }
                }
            },
            { file: InputStream ->
                when (ArchiveStreamFactory.detect(BufferedInputStream(file))) {
                    ArchiveStreamFactory.ZIP, ArchiveStreamFactory.JAR -> FileType.ZIP
                    ArchiveStreamFactory.AR -> FileType.AR
                    ArchiveStreamFactory.TAR -> FileType.TAR
                    else -> {
                        FileType.PLAIN
                    }
                }
            },
            { _: InputStream ->
                FileType.PLAIN
            }
        )

        private fun detectFileType(file: BufferedInputStream) = detectFunctions.firstNotNullOf {
            try {
                file.mark(BUFFER_SIZE)
                it.invoke(file)
            } catch (e: Exception) {
                null
            } finally {
                file.reset()
            }
        }

        @JvmStatic
        //IMPORTANT: usage of a single temporary file in ContentValidator makes ArtifactValidator instance not thread safe
        fun validate(log: Log, validationProperties: ValidationPropertiesDTO, name: String, file: Path) =
            ArtifactValidator(validationProperties, log).validate(name, file)
    }
}