package org.octopusden.octopus.dms.client.service;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactServiceImplTest {

    private final ArtifactServiceImpl service = new ArtifactServiceImpl();

    @Test
    void aFailedArtifactMessageIsLoggedOnceWithoutAStackTraceAtErrorLevel() throws Exception {
        RecordingLog log = new RecordingLog();

        MojoFailureException failure = assertThrows(MojoFailureException.class, () ->
                service.processArtifacts(
                        log,
                        "com.example",
                        "my-component",
                        "1.0",
                        null,
                        "distribution",
                        null,
                        null,
                        null,
                        "failing-artifact.deb,ok-artifact.deb",
                        null,
                        null,
                        1,
                        targetArtifact -> {
                            if (targetArtifact.coordinates.toPath().equals("failing-artifact.deb")) {
                                throw new RuntimeException("Artifact 'failing-artifact.deb' was not found in any " +
                                        "of the repositories [dms-deb-release-local]. This usually means it was " +
                                        "never published.");
                            }
                        }
                ));

        assertTrue(failure.getMessage().contains("1 of 2 artifact(s) failed validation"));
        assertTrue(failure.getMessage().contains("failing-artifact.deb"));
        assertTrue(failure.getMessage().contains("This usually means it was never published"));

        assertEquals(1, log.errorMessages.size());
        assertTrue(log.errorMessages.get(0).contains("failing-artifact.deb"));
        assertFalse(log.errorWithThrowableCalled, "error(Throwable) must not be used — it dumps a full stack trace");

        assertEquals(1, log.debugWithThrowable.size());
        assertTrue(log.debugWithThrowable.get(0).getValue().getMessage().contains("failing-artifact.deb"));
    }

    private static final class RecordingLog implements Log {
        final List<String> errorMessages = new ArrayList<>();
        final List<java.util.Map.Entry<String, Throwable>> debugWithThrowable = new ArrayList<>();
        boolean errorWithThrowableCalled = false;

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
            debugWithThrowable.add(new java.util.AbstractMap.SimpleEntry<>(content == null ? null : content.toString(), error));
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
        }

        @Override
        public void info(CharSequence content, Throwable error) {
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            errorMessages.add(content == null ? null : content.toString());
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            errorWithThrowableCalled = true;
        }

        @Override
        public void error(Throwable error) {
            errorWithThrowableCalled = true;
        }
    }
}
