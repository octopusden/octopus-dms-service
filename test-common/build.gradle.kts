dependencies {
    implementation(project(":common"))
    implementation(project(":client"))

    implementation(platform("org.junit:junit-bom:${project.properties["junit.version"]}"))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.junit.jupiter:junit-jupiter-params")

    implementation(platform("com.fasterxml.jackson:jackson-bom:${project.properties["jackson.version"]}"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.postgresql:postgresql:${project.properties["postgresql.version"]}")
}
