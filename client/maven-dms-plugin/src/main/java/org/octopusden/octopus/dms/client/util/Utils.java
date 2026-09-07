package org.octopusden.octopus.dms.client.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.octopusden.octopus.dms.client.RuntimeMojoExecutionException;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;

public class Utils {
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private Utils() {
    }

    /**
     * Reads and parses the given file as JSON of the given type, or returns {@code null} when
     * there is nothing to parse in it. The byte stream goes to the parser untouched (after a
     * leading UTF-8 BOM is consumed, which Jackson's encoding detection only does when further
     * content follows), so a file of 0 bytes, whitespace only, or BOM only yields {@code null},
     * and UTF-16 / BOM-prefixed UTF-8 documents parse exactly as for
     * {@code objectMapper.readValue(file, type)}. Malformed content surfaces as the parser's
     * {@link IOException} - the caller decides how to report it.
     *
     * @param file file to read; may be {@code null}
     * @param objectMapper mapper to parse with
     * @param type target type
     * @param <T> target type
     * @return parsed content, or {@code null} when the file is {@code null}, not a regular file
     * (a directory, for example), or contains nothing to parse
     * @throws IOException when the file cannot be read or parsed
     */
    public static <T> T readJsonIfNotBlank(final File file, final ObjectMapper objectMapper, final Class<T> type) throws IOException {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (JsonParser parser = objectMapper.getFactory().createParser(skipUtf8Bom(file))) {
            return parser.nextToken() == null ? null : objectMapper.readValue(parser, type);
        }
    }

    private static InputStream skipUtf8Bom(final File file) throws IOException {
        final InputStream in = new BufferedInputStream(new FileInputStream(file));
        in.mark(UTF8_BOM.length);
        final byte[] prefix = new byte[UTF8_BOM.length];
        if (in.read(prefix) != UTF8_BOM.length
                || prefix[0] != UTF8_BOM[0]
                || prefix[1] != UTF8_BOM[1]
                || prefix[2] != UTF8_BOM[2]
        ) {
            in.reset();
        }
        return in;
    }

    public static synchronized void writeToLogFile(String message, Path log) {
        if (log != null) {
            writeToFile(new ByteArrayInputStream((message + "\n---\n").getBytes(StandardCharsets.UTF_8)), log);
        }
    }

    public static void writeToFile(InputStream source, Path target) {
        try (OutputStream outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            byte[] buffer = new byte[4096];
            int size;
            while (-1 != (size = source.read(buffer))) {
                outputStream.write(buffer, 0, size);
            }
        } catch (IOException e) {
            throw new RuntimeMojoExecutionException(e.getMessage(), e);
        }
    }

    public static String calculateGroupId(final String groupIdPrefix, final String component, final ArtifactType type) {
        return groupIdPrefix + "." + component + "." + type;
    }
}
