package com.lab.edms.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class AssigneeRefListConverter implements AttributeConverter<List<AssigneeRef>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public String convertToDatabaseColumn(List<AssigneeRef> list) {
        try {
            return MAPPER.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            throw new RuntimeException("AssigneeRef 직렬화 실패", e);
        }
    }

    @Override
    public List<AssigneeRef> convertToEntityAttribute(String json) {
        try {
            return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("AssigneeRef 역직렬화 실패: " + json, e);
        }
    }
}
