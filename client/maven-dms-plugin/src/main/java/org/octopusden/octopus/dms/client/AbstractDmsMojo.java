package org.octopusden.octopus.dms.client;

import org.octopusden.octopus.dms.client.impl.ClassicDmsServiceClient;
import org.octopusden.octopus.dms.client.impl.DmsServiceClientParametersProvider;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.commons.lang3.StringUtils.isBlank;

abstract public class AbstractDmsMojo extends AbstractMojo {
    @Parameter(property = "dms.url", required = true)
    protected String dmsUrl;

    @Parameter(property = "dms.token")
    protected String token;

    @Parameter(property = "dms.username")
    protected String username;

    @Parameter(property = "dms.password")
    protected String password;

    @Parameter(property = "component", required = true)
    protected String component;

    @Parameter(property = "version", required = true)
    protected String version;

    @Parameter(property = "skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(property = "dryRun", defaultValue = "false")
    protected boolean dryRun;

    @Parameter(property = "parallelism", defaultValue = "10")
    protected int parallelism;

    protected void validateCredentials() throws MojoFailureException {
        if (isBlank(token) && (isBlank(username) || isBlank(password))) {
            throw new MojoFailureException("Either dms.token or dms.username + dms.password must be provided");
        }
    }

    protected DmsServiceUploadingClient getDmsServiceClient() {
        return new ClassicDmsServiceClient(new DmsServiceClientParametersProvider() {
            @NotNull
            @Override
            public String getApiUrl() {
                return dmsUrl;
            }

            @Nullable
            @Override
            public String getBearerToken() {
                return token;
            }

            @Override
            public String getBasicCredentials() {
                return username + ":" + password;
            }
        });
    }
}
