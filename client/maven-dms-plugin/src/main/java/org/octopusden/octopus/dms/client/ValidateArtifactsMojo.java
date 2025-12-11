package org.octopusden.octopus.dms.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.octopusden.octopus.dms.client.common.dto.LicenseValidatorPropertiesDTO;
import org.octopusden.octopus.dms.client.common.dto.PropertiesDTO;
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO;
import org.octopusden.octopus.dms.client.service.ArtifactService;
import org.octopusden.octopus.dms.client.service.DMSService;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.octopusden.octopus.releng.dto.ComponentVersion;
import org.octopusden.octopus.util.FileFilter;
import org.octopusden.octopus.util.FileFilterConfig;

@Mojo(name = "validate-artifacts", requiresProject = false)
public class ValidateArtifactsMojo extends AbstractArtifactCoordinatesMojo {
    private final ArtifactService artifactService;
    private final DMSService dmsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Parameter(property = "excludeFiles")
    private String excludeFiles;

    @Parameter(property = "wlIgnore")
    private File wlIgnore;

    @Parameter(property = "skipLicenceValidation", defaultValue = "false")
    private boolean skipLicenceValidation;

    public static final String WL_LOG = "org.slf4j.simpleLogger.log.org.octopusden.octopus.tools.wl";
    @Inject
    public ValidateArtifactsMojo(ArtifactService artifactService, DMSService dmsService) {
        this.artifactService = artifactService;
        this.dmsService = dmsService;
        if (System.getProperty(WL_LOG) == null) {
            System.setProperty(WL_LOG, "WARN");
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        if (StringUtils.isBlank(artifactsCoordinates) &&
                StringUtils.isBlank(artifactsCoordinatesDeb) &&
                StringUtils.isBlank(artifactsCoordinatesRpm)
        ) {
            log.warn("Artifacts coordinates are not set. Do nothing");
            return;
        }
        validateCredentials();
        FileFilterConfig mojoParamFilterConfig = new FileFilterConfig(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                StringUtils.isBlank(excludeFiles) ? Collections.emptyList() : Arrays.stream(excludeFiles.split("\\s?([,;])\\s?")).collect(Collectors.toList()),
                Collections.emptyList()
        );
        FileFilterConfig localFilterConfig = null;
        if (wlIgnore != null && wlIgnore.exists()) {
            try {
                localFilterConfig = objectMapper.readValue(wlIgnore, FileFilterConfig.class);
            } catch (IOException e) {
                log.info(String.format("Can not deserialize %s, error:", wlIgnore), e);
            }
        }
        FileFilterConfig fileFilterConfig = FileFilter.mergeConfigs(mojoParamFilterConfig, localFilterConfig);
        Set<String> excludePatterns = new HashSet<>(
                fileFilterConfig.getExcludeDirs().size() + fileFilterConfig.getExcludeFiles().size()
        );
        for (String excludeDir : fileFilterConfig.getExcludeDirs()) {
            excludePatterns.add("**/" + excludeDir + "/**");
        }
        for (String excludeFile : fileFilterConfig.getExcludeFiles()) {
            excludePatterns.add("**/" + excludeFile + "/**");
        }
        final DmsServiceUploadingClient dmsServiceClient = getDmsServiceClient();
        PropertiesDTO dmsConfiguration = dmsServiceClient.getConfiguration();
        dmsConfiguration.getValidation().getContentValidation().getExclude().addAll(excludePatterns);
        if (log.isDebugEnabled()) {
            try {
                log.debug("Final validation configuration: " + new ObjectMapper().writeValueAsString(dmsConfiguration.getValidation()));
            } catch (JsonProcessingException e) {
                throw new RuntimeMojoExecutionException(e.getMessage(), e);
            }
        }
        ValidationPropertiesDTO originalValidation = dmsConfiguration.getValidation();
        ValidationPropertiesDTO validationToUse;
        if (skipLicenceValidation) {
            log.info("Skipping licence validation");
            LicenseValidatorPropertiesDTO disabledLicense = new LicenseValidatorPropertiesDTO(false, originalValidation.getLicenseValidation().getPattern());
            validationToUse = new ValidationPropertiesDTO(disabledLicense, originalValidation.getNameValidation(), originalValidation.getContentValidation());
        } else {
            validationToUse = originalValidation;
        }
        artifactService.processArtifacts(log,
                dmsConfiguration.getMavenGroupPrefix(),
                component, version,
                name, type, classifier,
                artifactsCoordinates, artifactsCoordinatesVersion,
                artifactsCoordinatesDeb,
                artifactsCoordinatesRpm,
                artifactsCoordinatesDocker,
                parallelism,
                targetArtifact ->
                        dmsService.validateArtifact(log,
                                dmsServiceClient,
                                targetArtifact.file,
                                uploadAttempts,
                                ComponentVersion.create(component, version),
                                targetArtifact.coordinates,
                                validationToUse,
                                !replace,
                                validationLog == null ? null : validationLog.toPath(),
                                dryRun
                        )
        );
    }
}
