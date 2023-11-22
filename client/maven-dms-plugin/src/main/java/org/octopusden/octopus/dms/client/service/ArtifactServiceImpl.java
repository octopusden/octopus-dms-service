package org.octopusden.octopus.dms.client.service;

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.GavDTO;
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.RpmArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.util.Utils;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.octopusden.octopus.escrow.dto.DistributionEntity;
import org.octopusden.octopus.escrow.dto.EscrowExpressionContext;
import org.octopusden.octopus.escrow.dto.FileDistributionEntity;
import org.octopusden.octopus.escrow.dto.MavenArtifactDistributionEntity;
import org.octopusden.octopus.escrow.utilities.DistributionUtilities;
import org.octopusden.octopus.escrow.utilities.EscrowExpressionParser;
import org.octopusden.releng.versions.NumericVersionFactory;
import org.octopusden.releng.versions.VersionNames;

@Named
@Singleton
public class ArtifactServiceImpl implements ArtifactService {
    private static final String PROHIBITED_SYMBOLS = ":,\\s";
    public static final Pattern GAV_PATTERN = Pattern.compile(String.format("^([^%1$s]+(:[^%1$s]+){1,3})$", PROHIBITED_SYMBOLS));
    public static final Pattern DEB_PATTERN = Pattern.compile(String.format("[^%1$s]+\\.deb", PROHIBITED_SYMBOLS));
    public static final Pattern RPM_PATTERN = Pattern.compile(String.format("[^%1$s]+\\.rpm", PROHIBITED_SYMBOLS));

    @Override
    public void processArtifacts(Log log,
                                 String groupIdPrefix,
                                 String component,
                                 String version,
                                 String name,
                                 String type,
                                 String classifier,
                                 String artifactsCoordinates,
                                 String artifactsCoordinatesVersion,
                                 String artifactsCoordinatesDeb,
                                 String artifactsCoordinatesRpm,
                                 int processParallelism,
                                 Consumer<TargetArtifact> processFunction) throws MojoExecutionException, MojoFailureException {
        VersionNames versionNamesStub = new VersionNames("", "", ""); //IMPORTANT: does not affect EscrowExpressionContext evaluation
        EscrowExpressionContext escrowExpressionContext = new EscrowExpressionContext(
                component, version, null, new NumericVersionFactory(versionNamesStub) //TODO: get version names from components registry even if they are not used?
        );
        Collection<DistributionEntity> entities = Collections.emptyList();
        if (StringUtils.isNotBlank(artifactsCoordinates)) {
            String gav = (String) EscrowExpressionParser.getInstance().parseAndEvaluate(artifactsCoordinates, escrowExpressionContext);
            entities = DistributionUtilities.parseDistributionGAV(gav);
            if (entities.size() > 1 && name != null) {
                throw new MojoExecutionException("The 'name' parameter should be set only if one artifact is specified in 'artifactsCoordinates' property");
            }
        }
        List<String> debEntities = Collections.emptyList();
        if (StringUtils.isNotBlank(artifactsCoordinatesDeb)) {
            String deb = (String) EscrowExpressionParser.getInstance().parseAndEvaluate(artifactsCoordinatesDeb, escrowExpressionContext);
            debEntities = Arrays.asList(deb.split(","));
        }
        List<String> rpmEntities = Collections.emptyList();
        if (StringUtils.isNotBlank(artifactsCoordinatesRpm)) {
            String rpm = (String) EscrowExpressionParser.getInstance().parseAndEvaluate(artifactsCoordinatesRpm, escrowExpressionContext);
            rpmEntities = Arrays.asList(rpm.split(","));
        }
        final ArtifactType targetType = ArtifactType.findByType(type);
        if (targetType == null) {
            throw new MojoExecutionException(String.format("type %s is not recognized", type));
        }
        //Bulk validation
        for (DistributionEntity entity : entities) {
            log.debug(String.format("Validate: '%s'", entity));
            if (entity instanceof FileDistributionEntity) {
                final URI fileURI = ((FileDistributionEntity) entity).getUri();
                if (!Files.isRegularFile(Paths.get(fileURI))) {
                    throw new MojoExecutionException("The specified file artifact '" + fileURI + "' doesn't exist");
                }
            } else if (entity instanceof MavenArtifactDistributionEntity) {
                String mavenEntity = ((MavenArtifactDistributionEntity) entity).getGav();
                if (!GAV_PATTERN.matcher(mavenEntity).matches()) {
                    throw new MojoFailureException(String.format("MAVEN entity '%s' does not match '%s'", mavenEntity, GAV_PATTERN));
                }
            } else {
                throw new MojoFailureException("Not supported distribution entity: " + entity);
            }
        }
        for (String debEntity : debEntities) {
            if (!DEB_PATTERN.matcher(debEntity).matches()) {
                throw new MojoFailureException(String.format("DEB entity '%s' does not match '%s'", debEntity, DEB_PATTERN));
            }
        }
        for (String rpmEntity : rpmEntities) {
            if (!RPM_PATTERN.matcher(rpmEntity).matches()) {
                throw new MojoFailureException(String.format("RPM entity '%s' does not match '%s'", rpmEntity, RPM_PATTERN));
            }
        }
        //Bulk processing
        final ExecutorService executorService = Executors.newFixedThreadPool(processParallelism);
        List<Future<?>> results = new ArrayList<>(entities.size() + debEntities.size() + rpmEntities.size());
        final boolean extractNameFromArtifactCoordinate = StringUtils.isBlank(name);
        for (DistributionEntity entity : entities) {
            File targetFile;
            MavenArtifactCoordinatesDTO targetCoordinates;
            log.info(String.format("Processing: '%s'", entity));
            if (entity instanceof FileDistributionEntity) {
                final FileDistributionEntity fileDistributionEntity = (FileDistributionEntity) entity;
                final Path filePath = Paths.get(fileDistributionEntity.getUri());
                targetFile = filePath.toFile();
                String[] fileName = targetFile.getName().split("\\.");
                String artifactId = name;
                if (extractNameFromArtifactCoordinate) {
                    if (fileDistributionEntity.getArtifactId().isPresent()) {
                        artifactId = fileDistributionEntity.getArtifactId().get();
                        log.info("Use artifactId '" + artifactId + "' from file URI");
                    } else if (!fileName[0].isEmpty()) {
                        artifactId = fileName[0];
                        log.info("Use file name '" + artifactId + "' as artifactId");
                    } else {
                        throw new MojoFailureException("Unable to calculate artifactId for file " + targetFile.getName());
                    }
                }
                targetCoordinates = new MavenArtifactCoordinatesDTO(new GavDTO(
                        Utils.calculateGroupId(groupIdPrefix, component, targetType),
                        artifactId,
                        version,
                        (fileName.length > 1) ? fileName[fileName.length - 1] : "jar",
                        fileDistributionEntity.getClassifier().orElse(classifier)
                ));
            } else if (entity instanceof MavenArtifactDistributionEntity) {
                targetFile = null;
                String gav = ((MavenArtifactDistributionEntity) entity).getGav();
                String[] structuredGav = gav.split(":");
                int structuredGavSize = structuredGav.length;
                if (structuredGavSize < 2 || structuredGavSize > 4) {
                    throw new MojoFailureException("Invalid MAVEN entity " + gav);
                }
                targetCoordinates = new MavenArtifactCoordinatesDTO(new GavDTO(
                        structuredGav[0],
                        structuredGav[1],
                        (StringUtils.isNotBlank(artifactsCoordinatesVersion)) ? artifactsCoordinatesVersion : version,
                        (structuredGavSize > 2) ? structuredGav[2] : "jar",
                        (structuredGavSize > 3) ? structuredGav[3] : null
                ));
            } else {
                throw new MojoFailureException("Not supported distribution entity: " + entity);
            }
            results.add(executorService.submit(() ->
                    processFunction.accept(new TargetArtifact(targetType, targetCoordinates, targetFile))
            ));
        }
        for (String debEntity : debEntities) {
            results.add(executorService.submit(() ->
                    processFunction.accept(new TargetArtifact(targetType, new DebianArtifactCoordinatesDTO(debEntity), null))
            ));
        }
        for (String rpmEntity : rpmEntities) {
            results.add(executorService.submit(() ->
                processFunction.accept(new TargetArtifact(targetType, new RpmArtifactCoordinatesDTO(rpmEntity), null))
            ));
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.HOURS)) {
                executorService.shutdownNow();
                throw new MojoFailureException("Process is probably hanged");
            }
        } catch (InterruptedException e) {
            throw new MojoFailureException("Process interrupted", e);
        }
        List<Exception> exceptions = new ArrayList<>(results.size());
        for (Future<?> result: results) {
            try {
                result.get();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (exceptions.size() > 0) {
            exceptions.forEach(log::error);
            throw new MojoFailureException(exceptions.size() + " exception(s) occurred");
        }
    }

    public static class TargetArtifact {
        public final ArtifactType type;
        public final ArtifactCoordinatesDTO coordinates;
        public final File file;

        private TargetArtifact(ArtifactType type, ArtifactCoordinatesDTO coordinates, File file) {
            this.type = type;
            this.coordinates = coordinates;
            this.file = file;
        }
    }
}
