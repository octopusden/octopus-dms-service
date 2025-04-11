package org.octopusden.octopus.dms.client.service;

import org.octopusden.octopus.dms.client.DmsServiceUploadingClient;
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO;
import org.octopusden.octopus.releng.dto.ComponentVersion;
import java.io.File;
import java.nio.file.Path;
import org.apache.maven.plugin.logging.Log;

public interface DMSService {
    void validateArtifact(Log log, DmsServiceUploadingClient dmsServiceClient, File file, ComponentVersion componentVersion, ArtifactCoordinatesDTO coordinates, ValidationPropertiesDTO validationConfiguration, boolean failOnAlreadyExists, Path validationLog, boolean dryRun);

    void uploadArtifact(Log log, DmsServiceUploadingClient dmsServiceClient, File file, ComponentVersion componentVersion, ArtifactType type, ArtifactCoordinatesDTO coordinates, boolean failOnAlreadyExists, Path validationLog, boolean dryRun);

    void publish(Log log, DmsServiceUploadingClient dmsServiceClient, ComponentVersion componentVersion, boolean dryRun);
}
