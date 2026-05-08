package com.lab.edms.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan("com.lab.edms")
@EnableJpaRepositories("com.lab.edms")
public class JpaConfig {
}
