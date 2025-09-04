package org.octopusden.octopus.dms.client;

import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

abstract public class AbstractArtifactMojo extends AbstractDmsMojo {
    @Parameter(property = "type", required = true)
    protected String type;

    @Parameter(property = "classifier")
    protected String classifier;

    @Parameter(property = "name")
    protected String name;

    @Parameter(property = "replace", defaultValue = "true")
    protected boolean replace;

    @Parameter(property = "uploadAttempts", defaultValue = "3")
    protected int uploadAttempts;

    @Parameter(property = "validationLog")
    protected File validationLog;
}
