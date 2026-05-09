package com.lab.edms.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AuditPayloadSerializer {

    private final ObjectMapper objectMapper;

    public AuditPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }
}
