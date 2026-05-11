package com.lab.edms.common;

/**
 * HTTP 429 Too Many Requests 를 나타냅니다.
 * Rate limit 초과 시 사용됩니다 (21 CFR Part 11 — 브루트포스 방어).
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
