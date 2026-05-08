package com.lab.edms.auth;

public interface AuthProvider {
    AuthResult authenticate(String userId, String rawPassword, String clientIp);
    boolean supports(String providerCode);
}
