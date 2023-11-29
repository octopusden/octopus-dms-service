package org.octopusden.octopus.dms.client.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.octopusden.octopus.dms.client.RuntimeMojoExecutionException;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Utils {
    private Utils() {
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
