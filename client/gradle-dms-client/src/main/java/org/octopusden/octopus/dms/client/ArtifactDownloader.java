package org.octopusden.octopus.dms.client;

import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.impl.ClassicDmsServiceClient;
import org.octopusden.octopus.dms.client.impl.DmsServiceClientParametersProvider;
import feign.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient;
import org.octopusden.octopus.components.registry.core.dto.VersionNamesDTO;
import org.octopusden.releng.versions.NumericVersionFactory;
import org.octopusden.releng.versions.VersionNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactDownloader {
    private static final Logger logger = LoggerFactory.getLogger(ArtifactDownloader.class);
    private final ClassicDmsServiceClient dmsClient;
    private final ClassicComponentsRegistryServiceClient cregClient;

    public ArtifactDownloader(String dmsUrl, String dmsToken, String dmsUser, String dmsPassword, String cregUrl) {
        dmsClient = new ClassicDmsServiceClient(new DmsServiceClientParametersProvider() {
            @NotNull
            @Override
            public String getApiUrl() {
                return dmsUrl;
            }

            @Nullable
            @Override
            public String getBearerToken() {
                return dmsToken;
            }

            @Override
            public String getBasicCredentials() {
                return dmsUser + ":" + dmsPassword;
            }
        });
        cregClient = new ClassicComponentsRegistryServiceClient(() -> cregUrl);
    }

    public void download(String component, String version, ArtifactType type, boolean downloadPrevious, File targetDir, boolean clear, String startPreviousVersion) {
        try {
            logger.info("Exporting " + component + ":" + version + ":" + type + " to " + targetDir.getAbsolutePath());
            //noinspection ResultOfMethodCallIgnored
            targetDir.mkdirs();
            if (clear) {
                logger.info("Cleaning " + targetDir);
                FileUtils.cleanDirectory(targetDir);
            }
            downloadArtifacts(component, version, type, targetDir, ArtifactFullDTO::getFileName);
            if (downloadPrevious) {
                if (ArtifactType.REPORT != type) {
                    throw new Exception("Downloading previous lines latest versions is only allowed for " + ArtifactType.REPORT + "artifacts");
                }
                logger.info("Downloading previous lines latest versions reports from {}", startPreviousVersion);
                List<String> previousLinesVersions = dmsClient.getPreviousLinesLatestVersions(component, version, false).getVersions();
                logger.info("Previous lines latest versions for " + component + ":" + version + " are " + previousLinesVersions);
                VersionNamesDTO versionNames = cregClient.getVersionNames();
                NumericVersionFactory numericVersionFactory = new NumericVersionFactory(
                        new VersionNames(versionNames.getServiceBranch(), versionNames.getService(), versionNames.getMinor())
                );
                List<String> filteredPreviousLineVersions = previousLinesVersions.stream()
                        .filter(previousLineVersion ->
                                numericVersionFactory.create(previousLineVersion).compareTo(numericVersionFactory.create(startPreviousVersion)) > 0
                        ).collect(Collectors.toList());
                logger.info("Filtered previous line latest versions for " + component + ":" + version + " are " + filteredPreviousLineVersions);
                for (String previousLineVersion : filteredPreviousLineVersions) {
                    downloadArtifacts(component, previousLineVersion, ArtifactType.REPORT, targetDir, ArtifactFullDTO::getFileName);
                }
            }
        } catch (Exception e) {
            throw new GradleException(e.getMessage());
        }
    }

    private void downloadArtifacts(String component, String version, ArtifactType type, File targetDir, Function<ArtifactFullDTO, String> artifactNameCalculator) throws IOException {
        for (ArtifactShortDTO artifactShortDTO : dmsClient.getComponentVersionArtifacts(component, version, type).getArtifacts()) {
            logger.info("Downloading artifact id = {}", artifactShortDTO.getId());
            try (Response downloadArtifactResponse = dmsClient.downloadComponentVersionArtifact(component, version, artifactShortDTO.getId())) {
                if (downloadArtifactResponse.status() != 200) {
                    throw new IllegalStateException(IOUtils.toString(downloadArtifactResponse.body().asInputStream()));
                }
                String fileName = artifactNameCalculator.apply(dmsClient.getComponentVersionArtifact(component, version, artifactShortDTO.getId()));
                logger.info("Artifact id = {} file name is {}", artifactShortDTO.getId(), fileName);
                File file = new File(targetDir, fileName);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    IOUtils.copy(downloadArtifactResponse.body().asInputStream(), output);
                }
            }
        }
    }
}
