package org.octopusden.octopus.dms.client.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClient;
import org.octopusden.octopus.components.registry.core.dto.DistributionDTO;
import org.octopusden.octopus.dms.client.common.dto.GavDTO;
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO;
import org.octopusden.octopus.escrow.dto.DistributionEntity;
import org.octopusden.octopus.escrow.dto.MavenArtifactDistributionEntity;
import org.octopusden.octopus.escrow.utilities.DistributionUtilities;

/**
 * Turns {@code <component>:<version>} pairs into the coordinates of the artifacts those component
 * versions distribute.
 * <p>
 * Because the version belongs to the pair, components released on different version lines can be
 * uploaded in one invocation - which the single shared {@code artifacts.coordinates.version} cannot
 * express. The coordinates are not part of the pair: they are read from that component version's
 * {@code distribution.GAV} in the Components Registry, which keeps the registry the single source of
 * truth. Adding an artifact to a component therefore needs no change on the consumer side.
 * <p>
 * The lookup is expressed as {@link DistributionLookup} so that the resolution logic is testable
 * without HTTP, and this class deliberately knows nothing about the Maven plugin API - it reports
 * problems by appending to the caller's error list rather than throwing {@code MojoFailureException}.
 */
public class ComponentArtifactsResolver {
    private static final String DEFAULT_PACKAGING = "jar";

    /**
     * The part of the Components Registry this resolver needs: the distribution of one component
     * version. Returns {@code null} when the component declares no distribution.
     */
    public interface DistributionLookup {
        DistributionDTO distribution(String component, String version);
    }

    private final DistributionLookup lookup;

    public ComponentArtifactsResolver(final DistributionLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Resolves against a live Components Registry.
     */
    public static ComponentArtifactsResolver forUrl(final String cregUrl) {
        final ClassicComponentsRegistryServiceClient client =
                new ClassicComponentsRegistryServiceClient(() -> cregUrl);
        return new ComponentArtifactsResolver(
                (component, version) -> client.getDetailedComponent(component, version).getDistribution()
        );
    }

    /**
     * @param components comma (or pipe) separated {@code <component>:<version>} pairs
     * @param errors     cumulative list of errors, appended to so that every problem of a bulk
     *                   invocation is reported at once instead of failing on the first one
     * @return coordinates of every artifact the given component versions distribute, each at the
     *         version of the pair it came from, de-duplicated
     */
    public List<MavenArtifactCoordinatesDTO> resolve(final String components, final List<String> errors) {
        final Map<String, MavenArtifactCoordinatesDTO> resolved = new LinkedHashMap<>();
        if (StringUtils.isBlank(components)) {
            return new ArrayList<>(resolved.values());
        }
        for (String pair : components.split("[,|]")) {
            final String link = pair.trim();
            if (link.isEmpty()) {
                continue;
            }
            final String[] parts = link.split(":");
            if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                errors.add(String.format(
                        "Component '%s' does not match '<component>:<version>'", link
                ));
                continue;
            }
            resolveLink(parts[0].trim(), parts[1].trim(), resolved, errors);
        }
        return new ArrayList<>(resolved.values());
    }

    private void resolveLink(
            final String component,
            final String version,
            final Map<String, MavenArtifactCoordinatesDTO> resolved,
            final List<String> errors
    ) {
        final DistributionDTO distribution;
        try {
            distribution = lookup.distribution(component, version);
        } catch (Exception e) {
            errors.add(String.format(
                    "Unable to read the distribution of component '%s' version '%s': %s",
                    component, version, e.getMessage()
            ));
            return;
        }
        if (distribution == null || StringUtils.isBlank(distribution.getGav())) {
            errors.add(String.format(
                    "Component '%s' version '%s' has no distribution GAV defined",
                    component, version
            ));
            return;
        }
        final Collection<DistributionEntity> entities;
        try {
            entities = DistributionUtilities.parseDistributionGAV(distribution.getGav());
        } catch (RuntimeException e) {
            errors.add(String.format(
                    "Distribution GAV '%s' of component '%s' version '%s' is invalid: %s",
                    distribution.getGav(), component, version, e.getMessage()
            ));
            return;
        }
        for (DistributionEntity entity : entities) {
            if (entity instanceof MavenArtifactDistributionEntity) {
                final MavenArtifactDistributionEntity maven = (MavenArtifactDistributionEntity) entity;
                final MavenArtifactCoordinatesDTO coordinates = new MavenArtifactCoordinatesDTO(new GavDTO(
                        maven.getGroupId(),
                        maven.getArtifactId(),
                        version,
                        maven.getExtension().orElse(DEFAULT_PACKAGING),
                        maven.getClassifier().orElse(null)
                ));
                resolved.putIfAbsent(coordinates.toPath(), coordinates);
            } else {
                errors.add(String.format(
                        "Distribution of component '%s' version '%s' contains a non MAVEN entity '%s'",
                        component, version, entity
                ));
            }
        }
    }
}
