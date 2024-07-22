import com.google.protobuf.gradle.id

plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.8.0"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // coroutine
    val coroutineVersion = "1.6.4"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutineVersion")

    /*신규 추가*/
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // H2 Database
    runtimeOnly("com.h2database:h2:2.2.224")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.4")
    testImplementation(kotlin("test"))

    // jakarta inject
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    // jakarta
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:3.0.0")

    // coroutine for test
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")

    // protobuf
    implementation("com.google.protobuf:protobuf-kotlin:4.27.2")
    implementation("com.google.protobuf:protobuf-java:4.27.2")

    // Grpc
//    implementation("org.lognet:grpc-spring-boot-starter:4.8.0") // gRPC 스프링 부트 스타터
//    implementation("net.devh:grpc-spring-boot-starter:2.12.0.RELEASE")
    implementation("io.grpc:grpc-netty-shaded:1.65.1")
    implementation("io.grpc:grpc-protobuf:1.65.1")
    implementation("io.grpc:grpc-stub:1.65.1")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("io.grpc:protoc-gen-grpc-kotlin:1.4.1")
    implementation("io.grpc:protoc-gen-grpc-java:1.65.1")
}
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.2" // 최신 protobuf 컴파일러 버전으로 교체
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.65.1"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
//            task.plugins {
//                id("grpc") {}
//            }
            task.plugins {
                id("grpc")
//                id("grpckt")
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
