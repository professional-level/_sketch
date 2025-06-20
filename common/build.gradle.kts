plugins {
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.8.0"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.example.common"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.0")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(kotlin("test"))
    
    // reactive
    implementation("io.smallrye.reactive:mutiny-kotlin:2.3.0")
    
    // Hibernate Reactive
    implementation("org.hibernate.reactive:hibernate-reactive-core:2.3.0.Final")
    
    // jakarta inject
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    
    // protobuf
    implementation("com.google.protobuf:protobuf-kotlin:3.25.2")
    implementation("com.google.protobuf:protobuf-java:3.25.2")
    
    // kotlin reflection
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // ArchUnit for common architecture testing
    implementation("com.tngtech.archunit:archunit:1.3.0")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.2" // 최신 protobuf 컴파일러 버전으로 교체
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
