package org.octopusden.octopus.dms.client


import org.octopusden.octopus.dms.client.DmsServiceUploadingClient
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.GavDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.impl.ClassicDmsServiceClient
import org.octopusden.octopus.dms.client.impl.DmsServiceClientParametersProvider
import org.gradle.api.GradleException

class DmsPluginExtension {
    String dmsUrl
    String dmsToken
    String dmsUser
    String dmsPassword
    String mavenUser
    String mavenPassword
    boolean mavenAllowInsecureProtocol = false
    boolean ignoreDMSRepository = false

    private ClassicDmsServiceClient client

    DmsServiceUploadingClient getClient() {
        if (client == null) {
            client = new ClassicDmsServiceClient(new DmsServiceClientParametersProvider() {
                @Override
                String getApiUrl() {
                    return dmsUrl
                }

                @Override
                String getBearerToken() {
                    return dmsToken
                }

                @Override
                String getBasicCredentials() {
                    return "$dmsUser:$dmsPassword"
                }
            })
        }
        return client
    }

    Map<String, String> dmsProduct(Map dependency = [:]) {
        final String componentName = dependency['componentName']
        if (componentName == null) {
            throw new GradleException("The component name ['componentName'] must be set")
        }
        final String componentVersion = dependency['componentVersion']
        if (componentVersion == null) {
            throw new GradleException("The component version ['componentVersion'] must be set")
        }
        final String type = dependency['type']
        if (type == null) {
            throw new GradleException("The type ['type'] must be set")
        }
        final String groupId = dependency['groupId']
        final String artifactId = dependency['artifactId']
        if (artifactId == null) {
            throw new GradleException("The artifactId ['artifactId'] must be set")
        }
        final String version = dependency['version'] ?: componentVersion
        final String packaging = dependency['packaging']
        final String classifier = dependency['classifier']
        final GavDTO gav
        try {
            gav = getClient().getComponentVersionArtifacts(componentName, componentVersion, ArtifactType.findByType(type))
                    .artifacts
                    .findAll() { a -> a.repositoryType == RepositoryType.MAVEN }
                    .collect { a -> getClient().getComponentVersionArtifact(componentName, componentVersion, a.id) as MavenArtifactFullDTO }
                    .find { m ->
                        (groupId?.equals(m.gav.groupId) ?: true) &&
                                artifactId == m.gav.artifactId &&
                                version == m.gav.version &&
                                (packaging?.equals(m.gav.packaging) ?: true) &&
                                (classifier?.equals(m.gav.classifier) ?: true)
                    }?.gav
            if (gav == null) {
                throw new GradleException("There is no artifact with${(groupId) ? " groupId=$groupId" : ''} artifactId=$artifactId version=$version${(packaging) ? " packaging=$packaging" : ''}${(classifier) ? " classifier=$classifier" : ''} for $componentName:$componentVersion in DMS")
            }
        } catch (Exception e) {
            throw new GradleException("Fail to get maven GAV due to the " + e.getMessage())
        }
        def mavenGAVMap = new HashMap<String, String>()
        mavenGAVMap["group"] = gav.groupId
        mavenGAVMap["name"] = gav.artifactId
        mavenGAVMap["version"] = gav.version
        mavenGAVMap["ext"] = gav.packaging
        if (gav.classifier != null) {
            mavenGAVMap["classifier"] = gav.classifier
        }
        return mavenGAVMap
    }

    List<String> dmsRepositoriesUrls() {
        return getClient().getRepositories(RepositoryType.MAVEN)
    }
}
