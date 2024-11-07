package org.octopusden.octopus.dms.client.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        final ArtifactType targetType = ArtifactType.findByType(type);
        if (targetType == null) {
            throw new MojoExecutionException(String.format("type %s is not recognized", type));
        }
        if (
                (StringUtils.isNotBlank(artifactsCoordinatesDeb) || StringUtils.isNotBlank(artifactsCoordinatesRpm) || StringUtils.isNotBlank(artifactsCoordinatesDocker)
                ) &&
                        targetType != ArtifactType.DISTRIBUTION
        ) {
            throw new MojoFailureException("DEB, RPM or DOCKER coordinates are set, but type=" + targetType + " is not DISTRIBUTION");
        }

        //Bulk validation
        final List<String> errors = new ArrayList<>();
        if (StringUtils.isNotBlank(artifactsCoordinatesDocker) && !DOCKER_PATTERN.matcher(artifactsCoordinatesDocker).matches()) {
            errors.add("Docker image name contains invalid characters. The value must match pattern: " + DOCKER_PATTERN.pattern());
        }
        final String absoluteVersion = StringUtils.isNotBlank(artifactsCoordinatesVersion) ? artifactsCoordinatesVersion : version;
        if (StringUtils.isNotBlank(artifactsCoordinatesDocker) && "latest".equalsIgnoreCase(absoluteVersion)) {
            errors.add("Docker image tag contains invalid characters. Allowed characters are: a-z, A-Z, 0-9, ., _, -. Tag must not be 'latest'.");
        }

        final EscrowExpressionContext escrowExpressionContext = createEscrowExpressionContext(component, version);
        final Collection<DistributionEntity> entities = parseDistributionEntities(artifactsCoordinates, escrowExpressionContext, name);

        for (DistributionEntity entity : entities) {
            log.debug(String.format("Validate: '%s'", entity));
            if (entity instanceof FileDistributionEntity) {
                final URI fileURI = ((FileDistributionEntity) entity).getUri();
                if (!Files.isRegularFile(Paths.get(fileURI))) {
                    errors.add("The specified file artifact '" + fileURI + "' doesn't exist");
                }
            } else if (entity instanceof MavenArtifactDistributionEntity) {
                String mavenEntity = ((MavenArtifactDistributionEntity) entity).getGav();
                if (!GAV_PATTERN.matcher(mavenEntity).matches()) {
                    errors.add(String.format("MAVEN entity '%s' does not match '%s'", mavenEntity, GAV_PATTERN));
                }
            } else {
                errors.add("Not supported distribution entity: " + entity);
            }
        }
        final Map<String, Function<String, ArtifactCoordinatesDTO>> entitiesRep = new HashMap<>();
        entitiesRep.putAll(createEntities(artifactsCoordinatesDeb,
                escrowExpressionContext,
                DebianArtifactCoordinatesDTO::new,
                DEB_PATTERN,
                "DEB entity '%s' does not match '%s'",
                errors)
        );
        entitiesRep.putAll(createEntities(artifactsCoordinatesRpm,
                escrowExpressionContext,
                RpmArtifactCoordinatesDTO::new,
                RPM_PATTERN,
                "RPM entity '%s' does not match '%s",
                errors)
        );
        if (!errors.isEmpty()) {
            throw new MojoFailureException(errors.stream().collect(Collectors.joining("\n")));
        }

        //Bulk processing
        final ExecutorService executorService = Executors.newFixedThreadPool(processParallelism);
        final List<Future<?>> results = new ArrayList<>(entities.size());
        final boolean extractNameFromArtifactCoordinate = StringUtils.isBlank(name);
        for (DistributionEntity entity : entities) {
            File targetFile;
            MavenArtifactCoordinatesDTO targetCoordinates;
            log.info(String.format("Processing: '%s'", entity));
            if (entity instanceof FileDistributionEntity) {
                final FileDistributionEntity fileDistributionEntity = (FileDistributionEntity) entity;
                final Path filePath = Paths.get(fileDistributionEntity.getUri());
                targetFile = filePath.toFile();
                final String[] fileName = targetFile.getName().split("\\.");
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
                final String gav = ((MavenArtifactDistributionEntity) entity).getGav();
                final String[] structuredGav = gav.split(":");
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

        entitiesRep.forEach((key, creater) ->
            results.add(executorService.submit(() ->
                    processFunction.accept(new TargetArtifact(targetType, creater.apply(key), null))
            ))
        );

        if (StringUtils.isNotBlank(artifactsCoordinatesDocker)) {
            results.add(executorService.submit(() ->
                    processFunction.accept(new TargetArtifact(targetType, new DockerArtifactCoordinatesDTO(artifactsCoordinatesDocker, absoluteVersion), null))
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

    private Collection<DistributionEntity> parseDistributionEntities(String artifactsCoordinates, EscrowExpressionContext escrowExpressionContext, String name) throws MojoExecutionException {
        Collection<DistributionEntity> entities = Collections.emptyList();
        if (StringUtils.isNotBlank(artifactsCoordinates)) {
            String gav = (String) EscrowExpressionParser.getInstance().parseAndEvaluate(artifactsCoordinates, escrowExpressionContext);
            entities = DistributionUtilities.parseDistributionGAV(gav);
            if (entities.size() > 1 && name != null) {
                throw new MojoExecutionException("The 'name' parameter should be set only if one artifact is specified in 'artifactsCoordinates' property");
            }
        }
        return entities;
    }

        private EscrowExpressionContext createEscrowExpressionContext(String component, String version) {
        final VersionNames versionNamesStub = new VersionNames("", "", ""); //IMPORTANT: does not affect EscrowExpressionContext evaluation
        return new EscrowExpressionContext(
                component, version, null, new NumericVersionFactory(versionNamesStub)
        ); //TODO: get version names from components registry even if
    }

    private List<String> validateEntities(String[] entities, Pattern pattern, String message) {
        return Arrays.stream(entities)
                .filter(entity -> !pattern.matcher(entity).matches())
                .map(entity -> String.format(message, entity, pattern))
                .collect(Collectors.toList());
    }

    /**
     * List of entities
     *
     * @param artifactsCoordinates    - comma separated list of entities
     * @param escrowExpressionContext - context for expression evaluation
     * @param creater                 - function to create entity
     * @param pattern                 - pattern to validate entity
     * @param message                 - message for exception
     * @param errorsList              - list of errors
     * @return map of entities
     */
    private Map<String, Function<String, ArtifactCoordinatesDTO>> createEntities(String artifactsCoordinates,
                                                                                 EscrowExpressionContext escrowExpressionContext,
                                                                                 Function<String, ArtifactCoordinatesDTO> creater,
                                                                                 Pattern pattern,
                                                                                 String message,
                                                                                 List<String> errorsList) {
        if (StringUtils.isBlank(artifactsCoordinates)) {
            return Collections.emptyMap();
        }
        String entitiesStr = (String) EscrowExpressionParser.getInstance().parseAndEvaluate(artifactsCoordinates, escrowExpressionContext);
        final String[] entities = entitiesStr.split(",");
        final List<String> errors = validateEntities(entities, pattern, message);
        if (!errors.isEmpty()) {
            errorsList.addAll(errors);
            return Collections.emptyMap();
        }
        return Arrays.stream(entities)
                .collect(Collectors.toMap(Function.identity(), entity -> creater));
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
