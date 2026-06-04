plugins {
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.8.0"
    application
}

group = "com.example.flinkjob"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.0")
    }
}

val flinkVersion = "1.19.3"
val flinkKafkaConnectorVersion = "3.2.0-1.19"

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.google.protobuf:protobuf-java:3.25.2")
    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-clients:$flinkVersion")
    implementation("org.apache.flink:flink-connector-base:$flinkVersion")
    implementation("org.apache.flink:flink-connector-kafka:$flinkKafkaConnectorVersion")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.example.flinkjob.laor.LaorOrderLifecycleJobKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
