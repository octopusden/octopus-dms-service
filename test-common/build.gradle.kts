dependencies {
    implementation(project(":common"))
    implementation("org.octopusden.octopus.dms:client:${project.version}")
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.junit.jupiter:junit-jupiter-params")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.postgresql:postgresql:${project.properties["postgresql.version"]}")
}
