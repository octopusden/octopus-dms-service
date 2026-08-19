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
    /**
     * Components whose distribution artifacts are to be published, as comma (or pipe) separated
     * {@code <component>:<version>} pairs, for example
     * {@code first-component:1.0.32,second-component:2.4.7}.
     * <p>
     * Each pair carries its own version, so components released on different version lines can be
     * published in a single invocation - which {@link #artifactsCoordinatesVersion}, a single value
     * applied to every coordinate, cannot express. The artifact coordinates themselves are read from
     * that component version's {@code distribution.GAV} in the Components Registry, which is why
     * they are not repeated here.
     */
    @Parameter(property = "artifacts.components")
    protected String artifactsComponents;
    @Parameter(property = "creg.url")
    protected String cregUrl;
    @Parameter(property = "parallelism", defaultValue = "10")
    protected int parallelism;
}