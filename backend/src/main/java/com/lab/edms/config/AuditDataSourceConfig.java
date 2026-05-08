package com.lab.edms.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AuditDataSourceConfig {

    @Bean
    public DataSource auditDataSource(
            DataSourceProperties primary,
            @Value("${edms.audit.datasource.username}") String auditUser,
            @Value("${edms.audit.datasource.password}") String auditPassword
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(primary.getUrl());
        ds.setUsername(auditUser);
        ds.setPassword(auditPassword);
        ds.setDriverClassName(primary.getDriverClassName());
        ds.setMaximumPoolSize(5);
        ds.setPoolName("audit-pool");
        return ds;
    }

    @Bean
    public JdbcTemplate auditJdbcTemplate(DataSource auditDataSource) {
        return new JdbcTemplate(auditDataSource);
    }
}
