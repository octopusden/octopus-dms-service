package org.octopusden.octopus.dms.client.service;

import java.util.function.Consumer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

public interface ArtifactService {
    void processArtifacts(Log log,
                          String groupIdPrefix,
                          String component,
                          String version,
                          String name,
                          String type,
                          String classifier,
                          String artifactsCoordinates,
                          String artifactsCoordinatesVersion,
                          String artifactsCoordinatesDeb,
                          String artifactsCoordinatesRpm,
                          int processParallelism,
                          Consumer<ArtifactServiceImpl.TargetArtifact> processFunction) throws MojoExecutionException, MojoFailureException;
}
