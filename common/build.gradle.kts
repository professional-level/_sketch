plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.8.0"
//    kotlin("plugin.spring") version "1.8.0"
//    kotlin("plugin.jpa") version "1.8.0"
}

group = "com.example.common"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
//    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    // reactive
    implementation("io.smallrye.reactive:mutiny-kotlin:2.3.0")
    // Hibernate Reactive
    implementation("org.hibernate.reactive:hibernate-reactive-core:2.3.0.Final")
//    implementation("io.vertx:vertx-jdbc-client:4.5.7")
//    implementation("io.agroal:agroal-pool:2.3")
    // jakarta inject
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
