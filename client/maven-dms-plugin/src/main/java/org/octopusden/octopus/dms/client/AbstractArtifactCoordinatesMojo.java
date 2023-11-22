package org.octopusden.octopus.dms.client;

import org.apache.maven.plugins.annotations.Parameter;

abstract public class AbstractArtifactCoordinatesMojo extends AbstractArtifactMojo {
    @Parameter(property = "artifacts.coordinates")
    protected String artifactsCoordinates;
    @Parameter(property = "artifacts.coordinates.version")
    protected String artifactsCoordinatesVersion;
    @Parameter(property = "artifacts.coordinates.deb")
    protected String artifactsCoordinatesDeb;
    @Parameter(property = "artifacts.coordinates.rpm")
    protected String artifactsCoordinatesRpm;
}