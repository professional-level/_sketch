plugins {
    kotlin("jvm") version "1.8.0"
}

group = "com.example.architecture"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.tngtech.archunit:archunit:1.3.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
