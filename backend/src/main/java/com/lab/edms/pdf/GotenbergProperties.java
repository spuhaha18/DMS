package com.lab.edms.pdf;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gotenberg")
public record GotenbergProperties(String url, int timeoutSeconds) {}
