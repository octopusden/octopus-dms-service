package org.octopusden.octopus.dms.client.service;

import feign.Response;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.Validate;
import org.apache.maven.plugin.logging.Log;
import org.octopusden.octopus.dms.client.DmsServiceUploadingClient;
import org.octopusden.octopus.dms.client.RuntimeMojoExecutionException;
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.PatchComponentVersionDTO;
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO;
import org.octopusden.octopus.dms.client.common.dto.RepositoryType;
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO;
import org.octopusden.octopus.dms.client.util.Utils;
import org.octopusden.octopus.dms.client.validation.ArtifactValidator;
import org.octopusden.octopus.releng.dto.ComponentVersion;

@Named
@Singleton
public class DMSServiceImpl implements DMSService {

    private static final EnumSet<RepositoryType> NOT_DOWNLOADABLE_REPOSITORY_TYPES = EnumSet.of(RepositoryType.DOCKER);

    @Override
    public void validateArtifact(
            Log log,
            DmsServiceUploadingClient dmsServiceClient,
            File file,
            ComponentVersion componentVersion,
            ArtifactCoordinatesDTO coordinates,
            ValidationPropertiesDTO validationConfiguration,
            boolean failOnAlreadyExists,
            Path validationLog,
            boolean dryRun
    ) {
        log.info(String.format("Validate artifact '%s' for component '%s' version '%s', dry run '%s'", coordinates, componentVersion.getComponentName(), componentVersion.getVersion(), dryRun));
        if (!dryRun) {
            try {
                ArtifactDTO artifact;
                if (file != null) {
                    Validate.isTrue(file.exists(), "File should exist at " + file.getAbsolutePath());
                    try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                        artifact = dmsServiceClient.uploadArtifact((MavenArtifactCoordinatesDTO) coordinates, inputStream, file.getName(), failOnAlreadyExists);
                    }
                } else {
                    artifact = dmsServiceClient.addArtifact(coordinates, failOnAlreadyExists);
                }
                if (!NOT_DOWNLOADABLE_REPOSITORY_TYPES.contains(coordinates.getRepositoryType())) {
                    validateArtifact(log, dmsServiceClient, coordinates, validationConfiguration, validationLog, artifact.getId());
                }
            } catch (Exception e) {
                if (e.getMessage() != null) {
                    Utils.writeToLogFile(e.getMessage(), validationLog);
                }
                throw new RuntimeMojoExecutionException(String.format("Failed to validate artifact '%s' for component '%s' version '%s'", coordinates, componentVersion.getComponentName(), componentVersion.getVersion()), e);
            }
            Utils.writeToLogFile(String.format("Artifact '%s' is validated.", coordinates.toPath()), validationLog);
            log.info(String.format("Validated artifact '%s' for component '%s' version '%s'", coordinates.toPath(), componentVersion.getComponentName(), componentVersion.getVersion()));
        }
    }

    @Override
    public void uploadArtifact(
            Log log,
            DmsServiceUploadingClient dmsServiceClient,
            File file,
            ComponentVersion componentVersion,
            ArtifactType type,
            ArtifactCoordinatesDTO coordinates,
            boolean failOnAlreadyExists,
            Path validationLog,
            boolean dryRun
    ) {
        log.info(String.format("Upload %s artifact '%s' for component '%s' version '%s', dry run '%s'", type.value(), coordinates, componentVersion.getComponentName(), componentVersion.getVersion(), dryRun));
        if (!dryRun) {
            try {
                ArtifactDTO artifact;
                if (file != null) {
                    Validate.isTrue(file.exists(), "File should exist at " + file.getAbsolutePath());
                    try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                        artifact = dmsServiceClient.uploadArtifact((MavenArtifactCoordinatesDTO) coordinates, inputStream, file.getName(), failOnAlreadyExists);
                    }
                } else {
                    artifact = dmsServiceClient.addArtifact(coordinates, failOnAlreadyExists);
                }
                dmsServiceClient.registerComponentVersionArtifact(
                        componentVersion.getComponentName(),
                        componentVersion.getVersion(),
                        artifact.getId(),
                        new RegisterArtifactDTO(type),
                        failOnAlreadyExists
                );
            } catch (Exception e) {
                if (e.getMessage() != null) {
                    Utils.writeToLogFile(e.getMessage(), validationLog);
                }
                throw new RuntimeMojoExecutionException(String.format("Failed to upload %s artifact '%s' for component '%s' version '%s'", type.value(), coordinates, componentVersion.getComponentName(), componentVersion.getVersion()), e);
            }
            log.info(String.format("Uploaded %s artifact '%s' for component '%s' version '%s'", type.value(), coordinates.toPath(), componentVersion.getComponentName(), componentVersion.getVersion()));
        }
    }

    @Override
    public void publish(
            Log log,
            DmsServiceUploadingClient dmsServiceClient,
            ComponentVersion componentVersion,
            boolean dryRun
    ) {
        log.info(String.format("Publish component '%s' version '%s', dry run '%s'", componentVersion.getComponentName(), componentVersion.getVersion(), dryRun));
        if (!dryRun) {
            try {
                dmsServiceClient.patchComponentVersion(
                        componentVersion.getComponentName(),
                        componentVersion.getVersion(),
                        new PatchComponentVersionDTO(true)
                );
            } catch (Exception e) {
                throw new RuntimeMojoExecutionException(String.format("Failed to publish component '%s' version '%s'", componentVersion.getComponentName(), componentVersion.getVersion()), e);
            }
            log.info(String.format("Published component '%s' version '%s'", componentVersion.getComponentName(), componentVersion.getVersion()));
        }
    }

    /**
     * Validate artifact by downloading it and running validation rules.
     *
     * @param log                     - logger
     * @param dmsServiceClient        - DMS service client
     * @param coordinates             - artifact coordinates
     * @param validationConfiguration - validation configuration
     * @param validationLog           - validation log file
     * @param artifactId              - artifact ID
     * @throws Exception
     */
    private void validateArtifact(
            Log log,
            DmsServiceUploadingClient dmsServiceClient,
            ArtifactCoordinatesDTO coordinates,
            ValidationPropertiesDTO validationConfiguration,
            Path validationLog,
            Long artifactId
    ) throws Exception {
        Path artifactTempFile = Files.createTempFile(null, null);
        artifactTempFile.toFile().deleteOnExit();
        try (Response response = dmsServiceClient.downloadArtifact(artifactId)) {
            if (response.status() == 200) {
                Utils.writeToFile(response.body().asInputStream(), artifactTempFile);
            } else {
                Utils.writeToFile(response.body().asInputStream(), validationLog);
                throw new Exception("HTTP response status - " + response.status());
            }
        }
        String artifactPath = coordinates.toPath();
        int artifactNameStartPosition = artifactPath.lastIndexOf('/');
        List<String> validationErrors = ArtifactValidator.validate(
                log,
                validationConfiguration,
                (artifactNameStartPosition == -1) ? artifactPath : artifactPath.substring(artifactPath.lastIndexOf('/') + 1),
                artifactTempFile
        );
        if (!validationErrors.isEmpty()) {
            StringBuilder message = new StringBuilder(String.format("Artifact '%s' validation errors:", coordinates.toPath()));
            for (String validationError : validationErrors) {
                message.append('\n').append(validationError);
            }
            log.error(message.toString());
            Utils.writeToLogFile(message.toString(), validationLog);
            throw new Exception(String.format("Artifact '%s' is invalidated.", coordinates.toPath()));
        }
    }
}
