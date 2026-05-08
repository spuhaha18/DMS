package com.lab.edms.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Configuration
public class AuditDataSourceConfig {

    /**
     * Primary DataSource (app_role). Uses ObjectProvider for lazy JdbcConnectionDetails
     * resolution so that TestcontainersAutoConfiguration (auto-config) can register its
     * JdbcConnectionDetails before this bean is instantiated. Falls back to
     * DataSourceProperties for production where @ServiceConnection is not active.
     */
    @Bean
    @Primary
    public DataSource dataSource(
            ObjectProvider<JdbcConnectionDetails> connectionDetailsProvider,
            DataSourceProperties properties
    ) {
        JdbcConnectionDetails cd = connectionDetailsProvider.getIfAvailable();
        HikariDataSource ds = new HikariDataSource();
        if (cd != null) {
            ds.setJdbcUrl(cd.getJdbcUrl());
            ds.setUsername(cd.getUsername());
            ds.setPassword(cd.getPassword());
        } else {
            ds.setJdbcUrl(properties.getUrl());
            ds.setUsername(properties.getUsername());
            ds.setPassword(properties.getPassword());
            String driver = properties.determineDriverClassName();
            if (driver != null) ds.setDriverClassName(driver);
        }
        ds.setPoolName("primary-pool");
        return ds;
    }

    /**
     * Audit DataSource (audit_role) — INSERT-only on audit_logs/signature_manifests.
     * Same JDBC URL as the primary, but audit_role credentials for tamper-evidence.
     */
    @Bean
    public DataSource auditDataSource(
            ObjectProvider<JdbcConnectionDetails> connectionDetailsProvider,
            DataSourceProperties properties,
            @Value("${edms.audit.datasource.username}") String auditUser,
            @Value("${edms.audit.datasource.password}") String auditPassword
    ) {
        JdbcConnectionDetails cd = connectionDetailsProvider.getIfAvailable();
        String url = (cd != null) ? cd.getJdbcUrl() : properties.getUrl();
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(auditUser);
        ds.setPassword(auditPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(5);
        ds.setPoolName("audit-pool");
        return ds;
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcTemplate auditJdbcTemplate(@Qualifier("auditDataSource") DataSource auditDataSource) {
        return new JdbcTemplate(auditDataSource);
    }
}
