package org.octopusden.octopus.dms.client;

import javax.inject.Inject;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.octopusden.octopus.dms.client.service.DMSService;
import org.octopusden.octopus.releng.dto.ComponentVersion;

@Mojo(name = "publish", requiresProject = false)
public class PublishMojo extends AbstractDmsMojo {
    protected final DMSService dmsService;

    @Inject
    public PublishMojo(DMSService dmsService) {
        this.dmsService = dmsService;
    }

    @Override
    public void execute() throws MojoFailureException {
        validateCredentials();
        dmsService.publish(getLog(), getDmsServiceClient(), ComponentVersion.create(component, version), dryRun);
    }
}
