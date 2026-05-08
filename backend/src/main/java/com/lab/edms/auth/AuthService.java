package com.lab.edms.auth;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class AuthService {

    private final LocalAuthProvider provider;
    private final AuditService audit;

    public AuthService(LocalAuthProvider provider, AuditService audit) {
        this.provider = provider;
        this.audit = audit;
    }

    public AuthResult login(String userId, String rawPassword, String clientIp) {
        AuthResult result = provider.authenticate(userId, rawPassword, clientIp);

        AuditAction action = switch (result) {
            case AuthResult.Success __ -> AuditAction.USER_LOGIN_SUCCESS;
            case AuthResult.ForcePasswordChange __ -> AuditAction.USER_LOGIN_SUCCESS;
            case AuthResult.InvalidCredentials __ -> AuditAction.USER_LOGIN_FAIL;
            case AuthResult.AccountLocked __ -> AuditAction.USER_LOGIN_FAIL;
            case AuthResult.AccountDisabled __ -> AuditAction.USER_LOGIN_FAIL;
        };

        audit.log(new AuditEvent(
                userId, action, "USER", null,
                null, null, null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));

        if (result instanceof AuthResult.AccountLocked) {
            audit.log(new AuditEvent(
                    userId, AuditAction.USER_LOCKED, "USER", null,
                    null, null, "Account locked due to failed login attempts",
                    clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
        }

        return result;
    }

    public void logout(String userId, String clientIp) {
        audit.log(new AuditEvent(
                userId, AuditAction.USER_LOGOUT, "USER", null,
                null, null, null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
