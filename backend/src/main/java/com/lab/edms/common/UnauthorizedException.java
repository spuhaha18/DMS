package com.lab.edms.common;

/**
 * HTTP 401 Unauthorized 를 나타냅니다.
 * PW 재인증 실패 시 사용됩니다.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
