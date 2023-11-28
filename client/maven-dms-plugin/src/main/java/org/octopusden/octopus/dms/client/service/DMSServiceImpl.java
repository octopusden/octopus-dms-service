package org.octopusden.octopus.dms.client.service;

import org.octopusden.octopus.dms.client.DmsServiceUploadingClient;
import org.octopusden.octopus.dms.client.RuntimeMojoExecutionException;
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO;
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO;
import org.octopusden.octopus.dms.client.util.Utils;
import org.octopusden.octopus.dms.client.validation.ArtifactValidator;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.Validate;
import org.apache.maven.plugin.logging.Log;
import org.octopusden.octopus.releng.dto.ComponentVersion;

@Named
@Singleton
public class DMSServiceImpl implements DMSService {
    @Override
    public void validateArtifact(Log log,
                                 DmsServiceUploadingClient dmsServiceClient,
                                 File file,
                                 ComponentVersion componentVersion,
                                 ArtifactCoordinatesDTO coordinates,
                                 ValidationPropertiesDTO validationConfiguration,
                                 boolean failOnAlreadyExists,
                                 Path validationLog,
                                 boolean dryRun) {
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
                Path artifactTempFile = Files.createTempFile(null, null);
                artifactTempFile.toFile().deleteOnExit();
                try (Response response = dmsServiceClient.downloadArtifact(artifact.getId())) {
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
                if (validationErrors.size() > 0) {
                    StringBuilder message = new StringBuilder(String.format("Artifact '%s' validation errors:", coordinates));
                    for (String validationError : validationErrors) {
                        message.append('\n').append(validationError);
                    }
                    log.error(message.toString());
                    Utils.writeToFile(new ByteArrayInputStream(message.toString().getBytes(StandardCharsets.UTF_8)), validationLog);
                    throw new Exception(String.format("Artifact '%s' is invalidated.", coordinates));
                }
            } catch (Exception e) {
                if (e.getMessage() != null) {
                    Utils.writeToFile(new ByteArrayInputStream(e.getMessage().getBytes(StandardCharsets.UTF_8)), validationLog);
                }
                throw new RuntimeMojoExecutionException(String.format("Failed to validate artifact '%s' for component '%s' version '%s'", coordinates, componentVersion.getComponentName(), componentVersion.getVersion()), e);
            }
            Utils.writeToFile(new ByteArrayInputStream(String.format("Artifact '%s' is validated.", coordinates).getBytes(StandardCharsets.UTF_8)), validationLog);
            log.info(String.format("Validated artifact '%s' for component '%s' version '%s'", coordinates, componentVersion.getComponentName(), componentVersion.getVersion()));
        }
    }

    @Override
    public void uploadArtifact(Log log,
                               DmsServiceUploadingClient dmsServiceClient,
                               File file,
                               ComponentVersion componentVersion,
                               ArtifactType type,
                               ArtifactCoordinatesDTO coordinates,
                               boolean failOnAlreadyExists,
                               Path validationLog,
                               boolean dryRun) {
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
                    Utils.writeToFile(new ByteArrayInputStream(e.getMessage().getBytes(StandardCharsets.UTF_8)), validationLog);
                }
                throw new RuntimeMojoExecutionException(String.format("Failed to upload %s artifact '%s' for component '%s' version '%s'", type.value(), coordinates, componentVersion.getComponentName(), componentVersion.getVersion()), e);
            }
            log.info(String.format("Uploaded %s artifact '%s' for component '%s' version '%s'", type.value(), coordinates, componentVersion.getComponentName(), componentVersion.getVersion()));
        }
    }
}
