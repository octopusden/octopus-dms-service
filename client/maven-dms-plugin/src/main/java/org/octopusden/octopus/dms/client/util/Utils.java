package org.octopusden.octopus.dms.client.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.octopusden.octopus.dms.client.RuntimeMojoExecutionException;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.common.dto.GavDTO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

public class Utils {
    private static final String PROHIBITED_SYMBOLS = ":,\\s";

    /**
     * {@code groupId:artifactId[:packaging[:classifier[:version]]]}.
     * <p>
     * The five part form follows the Maven coordinate ordering and carries the version of that one
     * artifact, which is what lets artifacts released on different version lines be uploaded in a
     * single invocation. Its classifier may be left empty so that a version can still be given for
     * an artifact that has no classifier. Two to four part coordinates keep their previous meaning
     * and take the version from the {@code artifactsCoordinatesVersion} parameter shared by the
     * whole invocation.
     */
    private static final Pattern GAV_PATTERN = Pattern.compile(
            String.format(
                    "^([^%1$s]+(:[^%1$s]+){1,3}|[^%1$s]+:[^%1$s]+:[^%1$s]+:[^%1$s]*:[^%1$s]+)$",
                    PROHIBITED_SYMBOLS
            )
    );

    private Utils() {
    }

    public static Pattern getGavPattern() {
        return GAV_PATTERN;
    }

    public static boolean isValidMavenGav(final String gav) {
        return gav != null && GAV_PATTERN.matcher(gav).matches();
    }

    /**
     * Parses an artifact coordinate, taking the version from the coordinate itself when it carries
     * one and from {@code defaultVersion} otherwise.
     *
     * @throws MojoFailureException if the coordinate is malformed, or carries no version and no
     *                              {@code defaultVersion} was supplied.
     */
    public static GavDTO parseMavenGav(final String gav, final String defaultVersion) throws MojoFailureException {
        if (!isValidMavenGav(gav)) {
            throw new MojoFailureException("Invalid MAVEN entity " + gav);
        }
        final String[] parts = gav.split(":", -1);
        final String version = (parts.length > 4) ? parts[4] : defaultVersion;
        if (StringUtils.isBlank(version)) {
            throw new MojoFailureException("No version specified for MAVEN entity " + gav);
        }
        return new GavDTO(
                parts[0],
                parts[1],
                version,
                (parts.length > 2) ? parts[2] : "jar",
                (parts.length > 3 && !parts[3].isEmpty()) ? parts[3] : null
        );
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
