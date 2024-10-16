plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.8.0"
    kotlin("plugin.jpa") version "1.8.0"
    id("com.palantir.docker") version "0.36.0"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.example.stocksearchservice"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
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

    // reactive
    implementation("io.smallrye.reactive:mutiny-kotlin:2.3.0")

    /*신규 추가*/
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // JPA
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.2.4")

    // Hibernate Reactive
    implementation("org.hibernate.reactive:hibernate-reactive-core:2.3.0.Final")
    implementation("io.vertx:vertx-jdbc-client:4.5.7")
    implementation("io.agroal:agroal-pool:2.3")

    // H2 Database
    runtimeOnly("com.h2database:h2:2.2.224")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.4")
    testImplementation(kotlin("test"))

    // jdsl
    implementation("com.linecorp.kotlin-jdsl:jpql-dsl:3.4.1")
    implementation("com.linecorp.kotlin-jdsl:jpql-render:3.4.1")
    implementation("com.linecorp.kotlin-jdsl:spring-data-jpa-support:3.4.1")
    implementation("com.linecorp.kotlin-jdsl:hibernate-reactive-support:3.4.1")
    //
    noArg {
        annotation("com.linecorp.kotlinjdsl.example.hibernate.reactive.jakarta.jpql.entity.annotation.CompositeId")
    }

    allOpen {
        annotation("com.linecorp.kotlinjdsl.example.hibernate.reactive.jakarta.jpql.entity.annotation.CompositeId")
        annotation("jakarta.persistence.Entity")
        annotation("jakarta.persistence.Embeddable")
    }
    // jakarta inject
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    // jakarta
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:3.0.0")
    // arch unit
//    testImplementation("com.tngtech.archunit:archunit-junit5:0.23.1")
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    // protobuf
    implementation("com.google.protobuf:protobuf-kotlin:3.25.2")
    implementation("com.google.protobuf:protobuf-java:3.25.2")
    // kafka
    implementation("org.springframework.kafka:spring-kafka")
    // aop 추가
    implementation("org.springframework.boot:spring-boot-starter-aop")
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
