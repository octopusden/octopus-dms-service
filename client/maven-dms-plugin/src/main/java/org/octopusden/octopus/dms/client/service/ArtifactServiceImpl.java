package org.octopusden.octopus.dms.client.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.octopusden.octopus.escrow.dto.FileDistributionEntity;
import org.octopusden.octopus.escrow.dto.MavenArtifactDistributionEntity;
import org.octopusden.octopus.escrow.utilities.DistributionUtilities;

@Named
@Singleton
public class ArtifactServiceImpl implements ArtifactService {
    private static final String PROHIBITED_SYMBOLS = ":,\\s";
    /**
     * Separates a coordinate from the version it is published at, as in
     * {@code com.acme:docs:zip:english@1.0.32}. The coordinate itself is colon separated, so the
     * version cannot be appended with a colon - and it belongs to the coordinate rather than to the
     * invocation, which is what {@code artifacts.coordinates.version} cannot express.
     */
    private static final char COORDINATE_VERSION_SEPARATOR = '@';
    private static final Pattern GAV_PATTERN = Pattern.compile(String.format("^([^%1$s]+(:[^%1$s]+){1,3})$", PROHIBITED_SYMBOLS));
    private static final Pattern COORDINATE_VERSION_PATTERN = Pattern.compile(String.format("^[^%1$s]+$", PROHIBITED_SYMBOLS));
    private static final Pattern DEB_PATTERN = Pattern.compile(String.format("^[^%1$s]+\\.deb$", PROHIBITED_SYMBOLS));
    private static final Pattern RPM_PATTERN = Pattern.compile(String.format("^[^%1$s]+\\.rpm$", PROHIBITED_SYMBOLS));
    private static final Pattern DOCKER_PATTERN = Pattern.compile("^([a-z0-9]+([_.-][a-z0-9]+)*/)*[a-z0-9]+([_.-][a-z0-9]+)*:\\w[\\w.-]{0,127}$");

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
                                 String artifactsComponents,
                                 String cregUrl,
                                 int processParallelism,
                                 Consumer<TargetArtifact> processFunction) throws MojoExecutionException, MojoFailureException {
        final ArtifactType targetType = ArtifactType.findByType(type);
        if (targetType == null) {
            throw new MojoExecutionException(String.format("type %s is not recognized", type));
        }
        if ((StringUtils.isNotBlank(artifactsCoordinatesDeb) || StringUtils.isNotBlank(artifactsCoordinatesRpm) || StringUtils.isNotBlank(artifactsCoordinatesDocker)) && targetType != ArtifactType.DISTRIBUTION) {
            throw new MojoFailureException("DEB, RPM or DOCKER coordinates are set, but type=" + targetType + " is not DISTRIBUTION");
        }

        //Bulk validation
        final List<String> errors = new ArrayList<>();
        final List<VersionedEntity> distributionEntities = parseDistributionEntities(artifactsCoordinates, name, errors);
        for (VersionedEntity versionedEntity : distributionEntities) {
            final DistributionEntity entity = versionedEntity.entity;
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
        final List<MavenArtifactCoordinatesDTO> componentCoordinates =
                resolveComponentArtifacts(log, artifactsComponents, cregUrl, errors);
        // A coordinate that 'artifacts.components' already covers takes its version from the
        // component it belongs to, so the entry in 'artifacts.coordinates' is redundant and is
        // dropped. The mapping is not guessed from names - it is the Components Registry that says
        // which coordinates a component version distributes. Anything not covered is processed as
        // before, so nothing is silently left unpublished.
        // A coordinate stating its own version takes part in neither decision below: it needs
        // neither the shared version nor the version of a component that happens to cover it.
        final List<DistributionEntity> sharedVersionEntities = new ArrayList<>(distributionEntities.size());
        for (VersionedEntity versionedEntity : distributionEntities) {
            if (versionedEntity.version == null) {
                sharedVersionEntities.add(versionedEntity.entity);
            }
        }
        final Set<String> supersededGavs = supersededMavenGavs(sharedVersionEntities, componentCoordinates);
        final boolean mavenCoordinateNeedsSharedVersion = sharedVersionEntities
                .stream()
                .anyMatch(entity -> entity instanceof MavenArtifactDistributionEntity
                        && !supersededGavs.contains(((MavenArtifactDistributionEntity) entity).getGav()));
        if (mavenCoordinateNeedsSharedVersion
                && StringUtils.isNotBlank(artifactsCoordinatesVersion)
                && artifactsCoordinatesVersion.contains(",")) {
            errors.add(String.format(
                    "Version '%s' looks like a list of versions, but 'artifacts.coordinates.version' holds a single "
                            + "version applied to every coordinate. Artifacts released on different version lines "
                            + "state their version per coordinate, as '<coordinate>@<version>'.",
                    artifactsCoordinatesVersion
            ));
        }
        final Map<String, Function<String, ArtifactCoordinatesDTO>> entities = new HashMap<>();
        prepareEntities(
                artifactsCoordinatesDeb,
                DebianArtifactCoordinatesDTO::new,
                DEB_PATTERN,
                "DEB entity '%s' does not match '%s'",
                entities,
                errors
        );
        prepareEntities(
                artifactsCoordinatesRpm,
                RpmArtifactCoordinatesDTO::new,
                RPM_PATTERN,
                "RPM entity '%s' does not match '%s",
                entities,
                errors
        );
        prepareEntities(
                artifactsCoordinatesDocker,
                dockerEntity -> {
                    String[] imageAndTag = dockerEntity.split(":");
                    return new DockerArtifactCoordinatesDTO(imageAndTag[0], imageAndTag[1]);
                },
                DOCKER_PATTERN,
                "Docker entity '%s' does not match '%s",
                entities,
                errors
        );
        if (!errors.isEmpty()) {
            throw new MojoFailureException(String.join("\n", errors));
        }

        //Bulk target construction - every coordinate is built before anything is submitted, so that
        //an entity which fails to be built cannot leave a part of the invocation already published
        final List<TargetArtifact> targets = new ArrayList<>(distributionEntities.size() + componentCoordinates.size());
        final boolean extractNameFromArtifactCoordinate = StringUtils.isBlank(name);
        final String absoluteVersion = StringUtils.isNotBlank(artifactsCoordinatesVersion) ? artifactsCoordinatesVersion : version;
        for (VersionedEntity versionedEntity : distributionEntities) {
            final DistributionEntity distributionEntity = versionedEntity.entity;
            File targetFile;
            MavenArtifactCoordinatesDTO targetCoordinates;
            log.info(String.format("Processing: '%s'", distributionEntity));
            if (distributionEntity instanceof FileDistributionEntity) {
                final FileDistributionEntity fileDistributionEntity = (FileDistributionEntity) distributionEntity;
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
            } else if (distributionEntity instanceof MavenArtifactDistributionEntity) {
                targetFile = null;
                final String gav = ((MavenArtifactDistributionEntity) distributionEntity).getGav();
                if (supersededGavs.contains(gav)) {
                    log.info(String.format(
                            "MAVEN entity '%s' is covered by 'artifacts.components', taking its version from there",
                            gav
                    ));
                    continue;
                }
                final String[] structuredGav = gav.split(":");
                int structuredGavSize = structuredGav.length;
                if (structuredGavSize < 2 || structuredGavSize > 4) {
                    throw new MojoFailureException("Invalid MAVEN entity " + gav);
                }
                targetCoordinates = new MavenArtifactCoordinatesDTO(new GavDTO(
                        structuredGav[0],
                        structuredGav[1],
                        (versionedEntity.version != null) ? versionedEntity.version : absoluteVersion,
                        (structuredGavSize > 2) ? structuredGav[2] : "jar",
                        (structuredGavSize > 3) ? structuredGav[3] : null
                ));
            } else {
                throw new MojoFailureException("Not supported distribution entity: " + distributionEntity);
            }
            targets.add(new TargetArtifact(targetType, targetCoordinates, targetFile));
        }
        final Set<String> targetPaths = new HashSet<>();
        for (TargetArtifact target : targets) {
            targetPaths.add(target.coordinates.toPath());
        }
        for (MavenArtifactCoordinatesDTO coordinate : componentCoordinates) {
            if (targetPaths.add(coordinate.toPath())) {
                log.info(String.format("Processing component artifact: '%s'", coordinate));
                targets.add(new TargetArtifact(targetType, coordinate, null));
            } else {
                log.info(String.format(
                        "Component artifact '%s' is already listed in 'artifacts.coordinates', skipping the duplicate",
                        coordinate
                ));
            }
        }
        entities.forEach((entity, creater) -> targets.add(new TargetArtifact(targetType, creater.apply(entity), null)));

        //Bulk processing
        final ExecutorService executorService = Executors.newFixedThreadPool(processParallelism);
        final List<Future<?>> results = new ArrayList<>(targets.size());
        for (TargetArtifact target : targets) {
            results.add(executorService.submit(() -> processFunction.accept(target)));
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
        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            List<String> messages = new ArrayList<>(exceptions.size());
            for (Exception e : exceptions) {
                String message = describeFailure(e);
                messages.add(message);
                log.error(message);
                log.debug(message, e);
            }
            throw new MojoFailureException(String.format("%d of %d artifact(s) failed:%n%s",
                    exceptions.size(), results.size(), String.join("\n", messages)));
        }
    }

    private static String describeFailure(Throwable e) {
        Throwable effective = e.getCause() != null ? e.getCause() : e;
        String context = effective.getMessage() != null ? effective.getMessage() : effective.toString();
        Throwable root = effective;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root != effective && root.getMessage() != null && !context.contains(root.getMessage())) {
            return context + ": " + root.getMessage();
        }
        return context;
    }

    /**
     * The coordinates of {@code artifacts.coordinates} that {@code artifacts.components} already
     * covers, and whose version therefore comes from the component it belongs to rather than from
     * the shared {@code artifacts.coordinates.version}.
     * <p>
     * {@code artifacts.components} takes precedence over {@code artifacts.coordinates} for the
     * artifacts it covers; everything it does not cover is processed by the shared parameters as
     * before. Matching is by everything but the version, and it is the Components Registry that
     * states which coordinates a component version distributes, so the two sides are compared on
     * data rather than on a guess derived from names.
     */
    static Set<String> supersededMavenGavs(
            Collection<DistributionEntity> distributionEntities,
            List<MavenArtifactCoordinatesDTO> componentCoordinates
    ) {
        final Set<String> componentCoordinateKeys = new HashSet<>();
        for (MavenArtifactCoordinatesDTO coordinate : componentCoordinates) {
            componentCoordinateKeys.add(versionAgnosticKey(coordinate));
        }
        final Set<String> superseded = new HashSet<>();
        for (DistributionEntity entity : distributionEntities) {
            if (entity instanceof MavenArtifactDistributionEntity) {
                final MavenArtifactDistributionEntity maven = (MavenArtifactDistributionEntity) entity;
                if (componentCoordinateKeys.contains(versionAgnosticKey(maven))) {
                    superseded.add(maven.getGav());
                }
            }
        }
        return superseded;
    }

    /**
     * Identity of an artifact ignoring its version, so a coordinate listed without a version can be
     * matched against a coordinate the Components Registry resolved with one.
     */
    private static String versionAgnosticKey(MavenArtifactCoordinatesDTO coordinates) {
        return versionAgnosticKey(
                coordinates.getGav().getGroupId(),
                coordinates.getGav().getArtifactId(),
                coordinates.getGav().getPackaging(),
                coordinates.getGav().getClassifier()
        );
    }

    private static String versionAgnosticKey(MavenArtifactDistributionEntity entity) {
        return versionAgnosticKey(
                entity.getGroupId(),
                entity.getArtifactId(),
                entity.getExtension().orElse("jar"),
                entity.getClassifier().orElse(null)
        );
    }

    private static String versionAgnosticKey(String groupId, String artifactId, String packaging, String classifier) {
        return groupId + ':' + artifactId + ':' + packaging + ':' + (classifier == null ? "" : classifier);
    }

    /**
     * Resolves {@code <component>:<version>} pairs into artifact coordinates, each at the version of
     * the pair it came from. Coordinates come from the Components Registry, so this path does not go
     * through the shared, version agnostic coordinate format at all.
     */
    private List<MavenArtifactCoordinatesDTO> resolveComponentArtifacts(
            Log log,
            String artifactsComponents,
            String cregUrl,
            List<String> errors
    ) {
        if (StringUtils.isBlank(artifactsComponents)) {
            return Collections.emptyList();
        }
        if (StringUtils.isBlank(cregUrl)) {
            errors.add("'artifacts.components' is set but 'creg.url' is not, so they cannot be resolved");
            return Collections.emptyList();
        }
        log.info(String.format("Resolving artifacts of components '%s'", artifactsComponents));
        final List<MavenArtifactCoordinatesDTO> coordinates =
                ComponentArtifactsResolver.forUrl(cregUrl).resolve(artifactsComponents, errors);
        log.info(String.format("Components resolved to %s artifact(s)", coordinates.size()));
        return coordinates;
    }

    /**
     * An entry of {@code artifacts.coordinates} together with the version it states itself.
     */
    static final class VersionedEntity {
        final DistributionEntity entity;
        /** Version taken from the entry's own suffix, {@code null} when the entry states none. */
        final String version;

        VersionedEntity(final DistributionEntity entity, final String version) {
            this.entity = entity;
            this.version = version;
        }
    }

    /**
     * Splits {@code artifacts.coordinates} into its entries and reads the optional
     * {@code @<version>} suffix of each one, so that artifacts released on different version lines
     * can be published in a single invocation.
     * <p>
     * Entries are parsed one by one rather than as one string, which keeps the version of an entry
     * attached to the entity parsed from it - two entries may well name the same coordinate at
     * different versions. The suffix is stripped before {@link DistributionUtilities}, whose Maven
     * coordinate syntax does not admit it.
     * <p>
     * A file URI is left alone: {@code @} is a legitimate character in a path, and a file artifact
     * is published at the released version rather than at one stated by the coordinate.
     */
    private List<VersionedEntity> parseDistributionEntities(
            String artifactsCoordinates,
            String name,
            List<String> errors
    ) throws MojoExecutionException {
        final List<VersionedEntity> entities = new ArrayList<>();
        if (StringUtils.isBlank(artifactsCoordinates)) {
            return entities;
        }
        for (String rawEntry : artifactsCoordinates.split("[,|]")) {
            final String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String coordinate = entry;
            String entryVersion = null;
            final int separator = entry.lastIndexOf(COORDINATE_VERSION_SEPARATOR);
            if (separator >= 0 && !entry.startsWith("file:")) {
                coordinate = entry.substring(0, separator).trim();
                entryVersion = entry.substring(separator + 1).trim();
                if (coordinate.isEmpty() || !COORDINATE_VERSION_PATTERN.matcher(entryVersion).matches()) {
                    errors.add(String.format(
                            "Entry '%s' does not match '<coordinate>%s<version>'",
                            entry, COORDINATE_VERSION_SEPARATOR
                    ));
                    continue;
                }
            }
            for (DistributionEntity entity : DistributionUtilities.parseDistributionGAV(coordinate)) {
                entities.add(new VersionedEntity(entity, entryVersion));
            }
        }
        if (entities.size() > 1 && name != null) {
            throw new MojoExecutionException("The 'name' parameter should be set only if one artifact is specified in 'artifactsCoordinates' property");
        }
        return entities;
    }

    /**
     * Prepare entities
     *
     * @param artifactsCoordinates - comma separated list of entities
     * @param creater              - function to create entity
     * @param pattern              - pattern to validate entity
     * @param message              - message for exception
     * @param entities             - cumulative map of entities
     * @param errors               - cumulative list of errors
     */
    private void prepareEntities(
            String artifactsCoordinates,
            Function<String, ArtifactCoordinatesDTO> creater,
            Pattern pattern,
            String message,
            Map<String, Function<String, ArtifactCoordinatesDTO>> entities,
            List<String> errors
    ) {
        if (StringUtils.isNotBlank(artifactsCoordinates)) {
            for (String entity : artifactsCoordinates.split(",")) {
                if (pattern.matcher(entity).matches()) {
                    entities.put(entity, creater);
                } else {
                    errors.add(String.format(message, entity, pattern));
                }
            }
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
