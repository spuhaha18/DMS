package com.lab.edms.auth;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.user.PasswordHistory;
import com.lab.edms.user.PasswordHistoryRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AuthService {

    private final LocalAuthProvider provider;
    private final AuditService audit;
    private final UserRepository userRepo;
    private final PasswordHistoryRepository historyRepo;
    private final BCryptPasswordEncoder encoder;

    public AuthService(LocalAuthProvider provider, AuditService audit,
                       UserRepository userRepo, PasswordHistoryRepository historyRepo,
                       BCryptPasswordEncoder encoder) {
        this.provider = provider;
        this.audit = audit;
        this.userRepo = userRepo;
        this.historyRepo = historyRepo;
        this.encoder = encoder;
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

    @Transactional
    public ChangePasswordOutcome changePassword(String userId, String currentPw,
                                                String newPw, String clientIp) {
        User u = userRepo.findByUserId(userId).orElseThrow();

        if (u.getPasswordHash() == null || !encoder.matches(currentPw, u.getPasswordHash())) {
            return ChangePasswordOutcome.WRONG_CURRENT;
        }

        if (!(PasswordPolicyValidator.validate(newPw) instanceof PasswordPolicyValidator.Result.Ok)) {
            return ChangePasswordOutcome.POLICY_VIOLATION;
        }

        if (encoder.matches(newPw, u.getPasswordHash())) {
            return ChangePasswordOutcome.REUSED_RECENT;
        }

        List<PasswordHistory> recent = historyRepo.findByUserIdOrderByCreatedAtDesc(
                u.getId(), PageRequest.of(0, 4));
        for (PasswordHistory h : recent) {
            if (encoder.matches(newPw, h.getPwHash())) {
                return ChangePasswordOutcome.REUSED_RECENT;
            }
        }

        historyRepo.save(new PasswordHistory(u.getId(), u.getPasswordHash()));
        u.setPasswordHash(encoder.encode(newPw));
        u.setForceChangePw(false);
        userRepo.save(u);

        audit.log(new AuditEvent(userId, AuditAction.USER_PASSWORD_CHANGED,
                "USER", String.valueOf(u.getId()),
                null, null, null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
        return ChangePasswordOutcome.OK;
    }

    public enum ChangePasswordOutcome {
        OK, WRONG_CURRENT, POLICY_VIOLATION, REUSED_RECENT
    }
}
