plugins {
    // The Kotlin that Gradle itself embeds: this code compiles against, and runs inside, Gradle's
    // own API and stdlib, which an older compiler cannot read.
    kotlin("jvm") version embeddedKotlinVersion
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.danilopianini:khttp:1.2.2")
    implementation("org.mock-server:mockserver-client-java:5.11.1")
}
