package org.octopusden.octopus.dms.client

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class DmsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        final DmsPluginExtension dmsPluginExtension = project.extensions.create("dms", DmsPluginExtension.class)

        project.afterEvaluate {
            if (!dmsPluginExtension.ignoreDMSRepository && !project.gradle.startParameter.offline) {
                dmsPluginExtension.dmsRepositoriesUrls()
                    .each { repoUrl ->
                        project.logger.debug("The DMS repository URL is {}", repoUrl)
                        project.repositories.maven {
                            credentials {
                                username dmsPluginExtension.mavenUser != null ? dmsPluginExtension.mavenUser : project['NEXUS_USER']
                                password dmsPluginExtension.mavenPassword != null ? dmsPluginExtension.mavenPassword : project['NEXUS_PASSWORD']
                            }
                            url repoUrl
                            allowInsecureProtocol dmsPluginExtension.mavenAllowInsecureProtocol
                            if (GradleVersion.current() >= GradleVersion.version('6.0')) {
                                metadataSources {
                                    artifact()
                                }
                            }
                        }
                    }
            } else {
                project.logger.debug("Skip adding DMS repository {}, {}", dmsPluginExtension.ignoreDMSRepository, project.gradle.startParameter.offline)
                project.logger.info("The adding DMS repository disabled")
            }
        }
    }
}
