package com.lab.edms.auth;

import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class LocalAuthProvider implements AuthProvider {

    static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;

    public LocalAuthProvider(UserRepository userRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    @Override
    public boolean supports(String providerCode) {
        return "LOCAL".equals(providerCode);
    }

    @Override
    @Transactional
    public AuthResult authenticate(String userId, String rawPassword, String clientIp) {
        Optional<User> opt = userRepo.findByUserId(userId);
        if (opt.isEmpty()) {
            return new AuthResult.InvalidCredentials(MAX_FAILED_ATTEMPTS - 1);
        }
        User u = opt.get();

        if (u.getStatus() == UserStatus.DISABLED) return new AuthResult.AccountDisabled();
        if (u.getStatus() == UserStatus.LOCKED)   return new AuthResult.AccountLocked();

        if (u.getPasswordHash() == null) {
            return new AuthResult.InvalidCredentials(MAX_FAILED_ATTEMPTS - 1);
        }

        if (!encoder.matches(rawPassword, u.getPasswordHash())) {
            int next = u.getFailedAttempts() + 1;
            u.setFailedAttempts(next);
            if (next >= MAX_FAILED_ATTEMPTS) {
                u.setStatus(UserStatus.LOCKED);
                u.setLockedAt(OffsetDateTime.now());
                userRepo.save(u);
                return new AuthResult.AccountLocked();
            }
            userRepo.save(u);
            return new AuthResult.InvalidCredentials(MAX_FAILED_ATTEMPTS - next);
        }

        u.setFailedAttempts(0);
        userRepo.save(u);

        if (u.isForceChangePw()) return new AuthResult.ForcePasswordChange(u);
        return new AuthResult.Success(u);
    }
}
