package com.lab.edms.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ProblemDetail {

    private final String code;
    private final String message;
    private final String detail;
    private final OffsetDateTime timestamp;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    private ProblemDetail(String code, String message, String detail) {
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.timestamp = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static ProblemDetail of(String code, String message, String detail) {
        return new ProblemDetail(code, message, detail);
    }

    public static ProblemDetail of(String code, String message, String detail,
                                   String extraKey, Object extraValue) {
        ProblemDetail p = new ProblemDetail(code, message, detail);
        p.extras.put(extraKey, extraValue);
        return p;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getDetail() { return detail; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    @JsonAnyGetter public Map<String, Object> getExtras() { return extras; }
}
