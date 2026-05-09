package com.lab.edms.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditPayloadSerializerTest {

    private AuditPayloadSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        serializer = new AuditPayloadSerializer(objectMapper);
    }

    @Test
    void testToJson_WithValidObject() {
        // Arrange
        TestObject testObject = new TestObject("test-id", "test-name");

        // Act
        String result = serializer.toJson(testObject);

        // Assert
        assertNotNull(result);
        assertNotEquals("{}", result);
        assertTrue(result.contains("\"id\":\"test-id\""));
        assertTrue(result.contains("\"name\":\"test-name\""));
    }

    @Test
    void testToJson_WithNull_ReturnsEmptyJson() {
        // Act
        String result = serializer.toJson(null);

        // Assert
        assertEquals("null", result);
    }

    @Test
    void testToJson_WithSerializableObject() {
        // Arrange
        String stringValue = "test-value";

        // Act
        String result = serializer.toJson(stringValue);

        // Assert
        assertNotNull(result);
        assertEquals("\"test-value\"", result);
    }

    @Test
    void testToJson_WithNonSerializableObject_ReturnsEmptyJson() {
        // Arrange
        Object nonSerializableObject = new NonSerializableObject();

        // Act
        String result = serializer.toJson(nonSerializableObject);

        // Assert
        assertEquals("{}", result);
    }

    // Test helper classes
    static class TestObject {
        public String id;
        public String name;

        TestObject(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class NonSerializableObject {
        private final Thread threadField = new Thread(); // Thread is not serializable by Jackson

        public NonSerializableObject() {
        }
    }
}
