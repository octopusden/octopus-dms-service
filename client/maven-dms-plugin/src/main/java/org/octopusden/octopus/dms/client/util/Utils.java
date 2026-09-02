package org.octopusden.octopus.dms.client.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.commons.lang3.StringUtils;
import org.octopusden.octopus.dms.client.RuntimeMojoExecutionException;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;

public class Utils {
    private Utils() {
    }

    public static String readNonBlankContent(final File file) throws IOException {
        if (file == null || !file.isFile()) {
            return null;
        }
        final String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return StringUtils.isBlank(content) ? null : content;
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
