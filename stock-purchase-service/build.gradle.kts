plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.docker)
    alias(libs.plugins.protobuf)
}

group = "com.example.stockpurchaseservice"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Project dependencies
    implementation(project(":common"))
    implementation(project(":common-domain"))
    implementation(project(":common-infrastructure"))
    
    // Spring Boot
    implementation(libs.bundles.spring.boot.web)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.bundles.kotlin)
    
    // Coroutines
    implementation(libs.bundles.coroutines)
    
    // Reactive
    implementation(libs.mutiny.kotlin)
    
    // Database
    implementation(libs.bundles.database)
    runtimeOnly(libs.h2.database)
    
    // Query DSL
    implementation(libs.bundles.jdsl)
    
    // Jakarta
    implementation(libs.bundles.jakarta)
    
    // Logging
    implementation(libs.bundles.logging)
    
    // Protobuf
    implementation(libs.bundles.protobuf)
    
    // Kafka
    implementation(libs.spring.kafka)
    
    // Testing
    testImplementation(libs.bundles.testing)
    
    // JPA Configuration
    noArg {
        annotation("com.linecorp.kotlinjdsl.example.hibernate.reactive.jakarta.jpql.entity.annotation.CompositeId")
    }

    allOpen {
        annotation("com.linecorp.kotlinjdsl.example.hibernate.reactive.jakarta.jpql.entity.annotation.CompositeId")
        annotation("jakarta.persistence.Entity")
        annotation("jakarta.persistence.Embeddable")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.2"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}
kotlin {
    jvmToolchain(17)
}
tasks.withType<Test> {
    useJUnitPlatform()
}
//
// docker {
//    name = "${rootProject.name}-${project.name}:${version}"
//    setDockerfile(file("Dockerfile"))
//    files(tasks.bootJar.get().outputs.files)
//    buildArgs(mapOf("JAR_FILE" to tasks.bootJar.get().archiveFileName.get()))
// }
