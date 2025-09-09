import java.nio.charset.StandardCharsets
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

val pomFile = layout.buildDirectory.file("pom.xml").get().asFile

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                packaging = "maven-plugin"
                name.set(project.name)
                description.set("Octopus module: ${project.name}")
                url.set("https://github.com/octopusden/octopus-dms-service.git")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/kzaporozhtsev/octopus-dms-service.git")
                    connection.set("scm:git://github.com/octopusden/octopus-dms-service.git")
                }
                developers {
                    developer {
                        id.set("octopus")
                        name.set("octopus")
                    }
                }
            }
            pom.withXml {
                asNode().also {
                    it.appendNode("build").also { build ->
                        build.appendNode("directory", layout.buildDirectory.get().asFile.canonicalPath)
                        build.appendNode("outputDirectory", layout.buildDirectory.file("classes/java/main").get().asFile.canonicalPath)
                    }
                    it.appendNode("repositories").appendNode("repository").also {repository ->
                        repository.appendNode("id", "gradle-libs")
                        repository.appendNode("url", "https://repo.gradle.org/gradle/libs-releases")
                    }
                }
                pomFile.bufferedWriter(StandardCharsets.UTF_8).use {
                    it.write(asString().toString())
                }
            }
        }
    }
}

signing {
    isRequired = project.ext["signingRequired"] as Boolean
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}

@OptIn(ExperimentalStdlibApi::class)
tasks.register<Exec>("generatePluginDescriptor") {
    dependsOn("generatePomFileForMavenPublication", "compileJava", ":client:publishToMavenLocal")
    doFirst {
        assert(pomFile.exists()) { "${pomFile.canonicalPath}: was not generated" }
    }
    val mvnHome = System.getenv()["M2_HOME"] ?: System.getenv()["MAVEN_HOME"]
    val mavenCommand = if (System.getProperty("os.name").lowercase().contains("win")) "mvn.cmd" else "mvn"
    val cmd = "${mvnHome?.let { "$it/bin/" } ?: ""}$mavenCommand"
    this.setCommandLine(cmd, "-f", pomFile.canonicalPath, "-e", "-B", "org.apache.maven.plugins:maven-plugin-plugin:3.5:descriptor")
}

tasks["jar"].dependsOn("generatePluginDescriptor")

tasks["publishToMavenLocal"].dependsOn("test")

dependencies {
    implementation(project(":client"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.properties["spring-boot-legacy.version"]}"))
    implementation("org.springframework:spring-core")

    implementation("org.octopusden.octopus.infrastructure:component-resolver-core:${project.properties["octopus-components-registry-service.version"]}")
    implementation("org.octopusden.octopus.releng:versions-api:${project.properties["versions-api.version"]}")
    implementation("org.octopusden.octopus.tools.wl:validation:2.0.7")

    implementation("org.redline-rpm:redline:1.2.10")

    compileOnly("org.apache.maven:maven-plugin-api:3.3.9")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.4")

    testImplementation("org.apache.maven:maven-core:3.3.9")
    testImplementation("org.junit.jupiter:junit-jupiter")
}
