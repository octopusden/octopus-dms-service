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