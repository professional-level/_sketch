plugins {
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.8.0"
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.0")
    }
}

dependencies {
    implementation(project(":common"))
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    
    // Database
    implementation("io.r2dbc:r2dbc-h2")
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-jdbc-client:4.5.7")
    
    // Hibernate and JPA
    implementation("org.hibernate.reactive:hibernate-reactive-core:2.3.0.Final")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // HikariCP for connection pooling
    implementation("com.zaxxer:HikariCP:5.0.1")
    
    // H2 Database
    runtimeOnly("com.h2database:h2:2.2.224")
    
    // Reactive
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")
    implementation("io.smallrye.reactive:mutiny:2.5.1")
    implementation("io.smallrye.reactive:mutiny-kotlin:2.5.1")
    
    // Query DSL
    implementation("com.linecorp.kotlin-jdsl:jpql-dsl:3.4.1")
    implementation("com.linecorp.kotlin-jdsl:jpql-render:3.4.1")
    implementation("com.linecorp.kotlin-jdsl:spring-data-jpa-support:3.4.1")
    implementation("com.linecorp.kotlin-jdsl:hibernate-reactive-support:3.4.1")
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

kotlin {
    jvmToolchain(17)
}