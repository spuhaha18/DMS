package com.lab.edms;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers configuration shared across ALL Spring test contexts.
 *
 * Both containers are static — they survive @DirtiesContext context reloads.
 * Ryuk shuts them down when the JVM exits.
 *
 * Strategy: POSTGRES is returned via @Bean @ServiceConnection so Spring Boot
 * auto-registers JdbcConnectionDetails (required by AuditDataSourceConfig).
 * close()/stop() are overridden to no-ops so @DirtiesContext cannot kill the
 * static container — each new context reload calls postgresContainer() again
 * and gets the same already-running instance.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    // Override close/stop so @DirtiesContext context teardown cannot kill this container.
    // Raw type required for anonymous subclass — diamond inference is not supported here.
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final PostgreSQLContainer<?> POSTGRES =
            (PostgreSQLContainer<?>) new PostgreSQLContainer(DockerImageName.parse("postgres:16")) {
                @Override public void close() { /* keep running across context reloads */ }
                @Override public void stop()  { /* Ryuk will stop it on JVM exit      */ }
            }
            .withDatabaseName("edms_test")
            .withUsername("app_role")
            .withPassword("test_password")
            .withInitScript("init/create-audit-role.sql");

    public static final MinIOContainer MINIO =
            new MinIOContainer(DockerImageName.parse("minio/minio:latest"))
                    .withUserName("minio_test")
                    .withPassword("minio_test_pass");

    static {
        POSTGRES.start();
        MINIO.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }

    // MinIO has no @ServiceConnection support; use @DynamicPropertySource.
    // The static MINIO instance never changes port across reloads, so this is safe.
    @DynamicPropertySource
    static void minioProps(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",         MINIO::getS3URL);
        registry.add("minio.access-key",       MINIO::getUserName);
        registry.add("minio.secret-key",       MINIO::getPassword);
        registry.add("minio.bucket-original",  () -> "test-edms-documents-original");
        registry.add("minio.bucket-rendition", () -> "test-edms-documents-rendition");
        registry.add("minio.bucket-anchors",   () -> "test-edms-audit-anchors");
    }
}
