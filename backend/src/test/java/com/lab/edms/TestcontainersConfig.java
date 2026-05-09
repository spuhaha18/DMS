package com.lab.edms;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withDatabaseName("edms_test")
                .withUsername("app_role")
                .withPassword("test_password")
                .withInitScript("init/create-audit-role.sql");
    }

    public static final MinIOContainer MINIO =
            new MinIOContainer(DockerImageName.parse("minio/minio:latest"))
                    .withUserName("minio_test")
                    .withPassword("minio_test_pass");

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void minioProps(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",            MINIO::getS3URL);
        registry.add("minio.access-key",          MINIO::getUserName);
        registry.add("minio.secret-key",          MINIO::getPassword);
        registry.add("minio.bucket-original",     () -> "test-edms-documents-original");
        registry.add("minio.bucket-rendition",    () -> "test-edms-documents-rendition");
    }
}
