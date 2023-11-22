package org.octopusden.octopus.dms.client;

import org.octopusden.octopus.dms.client.service.ArtifactService;
import org.octopusden.octopus.dms.client.service.DMSService;
import org.octopusden.octopus.releng.dto.ComponentVersion;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "upload-artifacts", requiresProject = false)
public class UploadArtifactsMojo extends AbstractArtifactCoordinatesMojo {
    private final ArtifactService artifactService;
    private final DMSService dmsService;


    @Inject
    public UploadArtifactsMojo(ArtifactService artifactService, DMSService dmsService) {
        this.artifactService = artifactService;
        this.dmsService = dmsService;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        if (skip) {
            log.info("Skipping plugin execution");
            return;
        }
        if (StringUtils.isBlank(artifactsCoordinates) &&
                StringUtils.isBlank(artifactsCoordinatesDeb) &&
                StringUtils.isBlank(artifactsCoordinatesRpm)
        ) {
            log.warn("Artifacts coordinates are not set. Do nothing");
            return;
        }
        validateCredentials();
        final DmsServiceUploadingClient dmsServiceClient = getDmsServiceClient();
        artifactService.processArtifacts(log,
                dmsServiceClient.getConfiguration().getMavenGroupPrefix(),
                component, version,
                name, type, classifier,
                artifactsCoordinates, artifactsCoordinatesVersion,
                artifactsCoordinatesDeb,
                artifactsCoordinatesRpm,
                parallelism,
                targetArtifact ->
                        dmsService.uploadArtifact(log,
                                dmsServiceClient,
                                targetArtifact.file,
                                ComponentVersion.create(component, version),
                                targetArtifact.type,
                                targetArtifact.coordinates,
                                !replace,
                                validationLog == null ? null : validationLog.toPath(),
                                dryRun
                        )
        );
    }
}
