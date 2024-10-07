package org.octopusden.octopus.dms.client.service;

import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import org.octopusden.octopus.dms.client.common.dto.DebianArtifactCoordinatesDTO;
import org.octopusden.octopus.dms.client.common.dto.DockerArtifactCoordinatesDTO;
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
    private static final Pattern GAV_PATTERN = Pattern.compile(String.format("^([^%1$s]+(:[^%1$s]+){1,3})$", PROHIBITED_SYMBOLS));
    private static final Pattern DEB_PATTERN = Pattern.compile(String.format("[^%1$s]+\\.deb", PROHIBITED_SYMBOLS));
    private static final Pattern RPM_PATTERN = Pattern.compile(String.format("[^%1$s]+\\.rpm", PROHIBITED_SYMBOLS));
    private static final Pattern DOCKER_PATTERN = Pattern.compile("^(?:[a-z0-9]+(?:[._-][a-z0-9]+)*/)*[a-z0-9]+(?:[._-][a-z0-9]+)*$");
    private static final Pattern DOCKER_TAG_PATTERN = Pattern.compile("^(?![lL][aA][tT][eE][sS][tT]\\b)[a-zA-Z0-9._-]+$");

    /**
     * List of entities
     *
     * @param artifactsCoordinates    - comma separated list of entities
     * @param escrowExpressionContext - context for expression evaluation
     * @param creater                 - function to create entity
     * @param pattern                 - pattern to validate entity
     * @param message                 - message for exception
     * @return list of entities with their creators
     */
    private List<Pair<String, Function<String, ArtifactCoordinatesDTO>>> createEntities(String artifactsCoordinates,
                                EscrowExpressionContext escrowExpressionContext,
                                Function<String, ArtifactCoordinatesDTO> creater,
                                Pattern pattern,
                                String message) {
        if (StringUtils.isBlank(artifactsCoordinates)) {
            return Collections.emptyList();
        }
        String entitiesStr = (String) EscrowExpressionParser.getInstance().parseAndEvaluate(artifactsCoordinates, escrowExpressionContext);
        return Arrays.stream(entitiesStr.split(",")).map(item -> {
            if (!pattern.matcher(item).matches()) {
                throw new IllegalArgumentException(String.format(message, item, pattern));
            }
            return new ImmutablePair<>(item, creater);
        }).collect(Collectors.toList());
    }

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
                                 String artifactsCoordinatesDocker,
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
        //Bulk processing
        final ExecutorService executorService = Executors.newFixedThreadPool(processParallelism);
        List<Future<?>> results = new ArrayList<>(entities.size());
        final boolean extractNameFromArtifactCoordinate = StringUtils.isBlank(name);
        final String absoluteVersion = StringUtils.isNotBlank(artifactsCoordinatesVersion) ? artifactsCoordinatesVersion : version;
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
                        absoluteVersion,
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

        if (
                (StringUtils.isNotBlank(artifactsCoordinatesDeb) || StringUtils.isNotBlank(artifactsCoordinatesRpm) || StringUtils.isNotBlank(artifactsCoordinatesDocker)
                ) &&
                        targetType != ArtifactType.DISTRIBUTION
        ) {
            throw new MojoFailureException("DEB, RPM or DOCKER coordinates are set, but type=" + targetType + " is not DISTRIBUTION");
        }

        List<Pair<String, Function<String, ArtifactCoordinatesDTO>>> entitiesRep = new ArrayList<>();
        entitiesRep.addAll(createEntities(artifactsCoordinatesDeb,
                escrowExpressionContext,
                DebianArtifactCoordinatesDTO::new,
                DEB_PATTERN,
                "DEB entity '%s' does not match '%s'")
        );
        entitiesRep.addAll(createEntities(artifactsCoordinatesRpm,
                escrowExpressionContext,
                RpmArtifactCoordinatesDTO::new,
                RPM_PATTERN,
                "RPM entity '%s' does not match '%s")
        );
        entitiesRep.addAll(createEntities(artifactsCoordinatesDocker,
                escrowExpressionContext,
                image -> {
                    if (!DOCKER_TAG_PATTERN.matcher(image).matches()) {
                        throw new IllegalArgumentException("Docker image tag contains invalid characters. Allowed characters are: a-z, A-Z, 0-9, ., _, -. Tag must not be 'latest'");
                    }
                    return new DockerArtifactCoordinatesDTO(image, absoluteVersion);
                },
                DOCKER_PATTERN,
                "DOCKER entity '%s' does not match '%s")
        );
        for (Pair<String, Function<String, ArtifactCoordinatesDTO>> pair : entitiesRep) {
            String entity = pair.getLeft();
            Function<String, ArtifactCoordinatesDTO> creater = pair.getRight();

            results.add(executorService.submit(() ->
                    processFunction.accept(new TargetArtifact(targetType, creater.apply(entity), null))
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
