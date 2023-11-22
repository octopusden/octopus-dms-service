package org.octopusden.octopus.dms.client;

import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.common.dto.GavDTO;
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.service.DMSService;
import org.octopusden.octopus.dms.client.util.Utils;
import org.octopusden.octopus.releng.dto.ComponentVersion;
import java.io.File;
import javax.inject.Inject;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "upload", requiresProject = false)
public class UploadFileMojo extends AbstractArtifactMojo {
    protected final DMSService dmsService;

    @Parameter(property = "file", required = true)
    protected File file;

    @Inject
    public UploadFileMojo(DMSService dmsService) {
        this.dmsService = dmsService;
    }

    @Override
    public void execute() throws MojoFailureException {
        if (skip) {
            getLog().info("Execution skipped");
            return;
        }
        validateCredentials();
        final ArtifactType artifactType = ArtifactType.findByType(type);
        if (artifactType == null) {
            throw new MojoFailureException(String.format("type %s is not recognized", type));
        }
        String[] fileName = file.getName().split("\\.");
        final DmsServiceUploadingClient dmsServiceClient = getDmsServiceClient();
        dmsService.uploadArtifact(getLog(),
                dmsServiceClient,
                file,
                ComponentVersion.create(component, version),
                artifactType,
                new MavenArtifactCoordinatesDTO(new GavDTO(
                        Utils.calculateGroupId(
                                dmsServiceClient.getConfiguration().getMavenGroupPrefix(),
                                component,
                                artifactType
                        ),
                        name,
                        version,
                        (fileName.length > 1) ? fileName[fileName.length - 1] : "jar",
                        classifier
                )),
                !replace,
                validationLog == null ? null : validationLog.toPath(),
                dryRun);
    }
}
