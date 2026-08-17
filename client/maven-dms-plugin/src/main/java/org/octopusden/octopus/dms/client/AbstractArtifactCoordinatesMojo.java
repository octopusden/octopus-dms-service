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
     * Documentation components this component is documented by, as {@code <component>:<version>}
     * pairs, as resolved by the release infrastructure.
     * <p>
     * Each pair carries the version of that one documentation component, so components released on
     * different version lines can be uploaded in a single invocation. The artifact coordinates
     * themselves are read from the documentation component's {@code distribution.GAV} in the
     * Components Registry, which is why they are not repeated here.
     */
    @Parameter(property = "doc.components")
    protected String docComponents;
    @Parameter(property = "creg.url")
    protected String cregUrl;
    @Parameter(property = "parallelism", defaultValue = "10")
    protected int parallelism;
}