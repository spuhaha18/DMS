plugins {
    java
    id("org.springframework.boot") version "3.3.11"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.lab"
version = "0.1.0-SNAPSHOT"

// Override Spring Boot BOM's testcontainers version (1.19.8 uses Docker API 1.32,
// but current Docker daemon requires API >= 1.40; 1.20.x ships docker-java 3.3.x which uses 1.41+).
extra["testcontainers.version"] = "1.20.3"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.hibernate.orm:hibernate-envers")
    implementation("org.postgresql:postgresql")
    implementation("com.github.f4b6a3:ulid-creator:5.2.3")
    implementation("io.minio:minio:8.5.10")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testImplementation("org.testcontainers:minio:1.20.3")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Docker Engine 29.x requires API >= 1.40; testcontainers shaded docker-java defaults to 1.32.
    // Pass via both system property (api.version) and env var.
    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
    environment("DOCKER_API_VERSION", "1.41")
    systemProperty("api.version", "1.41")
    systemProperty("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
}
