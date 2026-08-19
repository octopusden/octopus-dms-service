package org.octopusden.octopus.dms.client;

import org.apache.maven.plugins.annotations.Parameter;

abstract public class AbstractArtifactCoordinatesMojo extends AbstractArtifactMojo {
    /**
     * Comma (or pipe) separated artifact coordinates, each optionally stating the version it is
     * published at as {@code <coordinate>@<version>}, for example
     * {@code com.acme:docs:zip:english@1.0.32,com.acme:docs:zip:spanish@1.0.8}.
     * <p>
     * A coordinate stating its own version can be published alongside coordinates released on other
     * version lines, which {@link #artifactsCoordinatesVersion} - one version applied to every
     * coordinate - cannot express. A coordinate without the suffix behaves as it always has.
     * <p>
     * A file URI is taken verbatim: {@code @} is a legitimate character in a path, and a file
     * artifact is published at the released version.
     */
    @Parameter(property = "artifacts.coordinates")
    protected String artifactsCoordinates;
    /**
     * Version applied to every coordinate of {@link #artifactsCoordinates} that does not state one
     * itself. Defaults to the version being released.
     */
    @Parameter(property = "artifacts.coordinates.version")
    protected String artifactsCoordinatesVersion;
    @Parameter(property = "artifacts.coordinates.deb")
    protected String artifactsCoordinatesDeb;
    @Parameter(property = "artifacts.coordinates.rpm")
    protected String artifactsCoordinatesRpm;
    @Parameter(property = "artifacts.coordinates.docker")
    protected String artifactsCoordinatesDocker;
    @Parameter(property = "parallelism", defaultValue = "10")
    protected int parallelism;
}