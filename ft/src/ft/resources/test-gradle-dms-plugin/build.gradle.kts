plugins {
    id("org.octopusden.octopus-dms")
}

dms {
    dmsUrl = project.properties["dms-service.url"] as String
    dmsUser = project.properties["dms-service.user"] as String
    dmsPassword = project.properties["dms-service.password"] as String
    mavenUser = "admin"
    mavenPassword = "password"
    mavenAllowInsecureProtocol = true
}

val dmsNotes by configurations.creating

dependencies {
    dmsNotes(dms.dmsProduct(mapOf(
        "componentName" to project.properties["component.name"] as String,
        "componentVersion" to project.properties["component.version"] as String,
        "type" to "notes",
        "artifactId" to project.properties["artifact.name"] as String,
        "version" to project.properties["artifact.version"] as String,
        "classifier" to project.properties["artifact.classifier"] as String
    )))
}

val downloadReleaseNotes by tasks.creating(Copy::class) {
    from(configurations["dmsNotes"])
    into(project.properties["target-dir"] as String)
}