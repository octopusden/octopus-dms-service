plugins {
    kotlin("jvm") version "1.9.22"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.danilopianini:khttp:1.2.2")
    implementation("org.mock-server:mockserver-client-java:5.11.1")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.2.12")
}
