plugins {
    kotlin("jvm") version "1.6.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.danilopianini:khttp:1.2.2")
    implementation("org.mock-server:mockserver-client-java:5.11.1")
}
