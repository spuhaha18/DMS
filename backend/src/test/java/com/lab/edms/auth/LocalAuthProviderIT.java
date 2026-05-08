package com.lab.edms.auth;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class LocalAuthProviderIT {

    @Autowired LocalAuthProvider provider;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;

    @Test
    void successfulAuthentication_returnsSuccess_andResetsFailedAttempts() {
        seedUser("alice", "Correct!Pass1");

        AuthResult result = provider.authenticate("alice", "Correct!Pass1", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        User u = userRepo.findByUserId("alice").orElseThrow();
        assertThat(u.getFailedAttempts()).isZero();
    }

    @Test
    void wrongPassword_incrementsFailedAttempts_andReturnsRemaining() {
        seedUser("bob", "Correct!Pass1");

        AuthResult result = provider.authenticate("bob", "wrong", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.InvalidCredentials.class);
        assertThat(((AuthResult.InvalidCredentials) result).remainingAttempts()).isEqualTo(4);
        assertThat(userRepo.findByUserId("bob").orElseThrow().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void fifthFailure_locksAccount() {
        seedUser("carol", "Correct!Pass1");

        for (int i = 0; i < 5; i++) {
            provider.authenticate("carol", "wrong", "10.0.0.1");
        }

        User u = userRepo.findByUserId("carol").orElseThrow();
        assertThat(u.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(u.getFailedAttempts()).isEqualTo(5);
        assertThat(u.getLockedAt()).isNotNull();
    }

    @Test
    void lockedAccount_returnsAccountLocked_evenWithCorrectPassword() {
        seedUser("dave", "Correct!Pass1");
        for (int i = 0; i < 5; i++) provider.authenticate("dave", "wrong", "10.0.0.1");

        AuthResult result = provider.authenticate("dave", "Correct!Pass1", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.AccountLocked.class);
    }

    @Test
    void unknownUser_returnsInvalidCredentials_withoutDisclosingExistence() {
        AuthResult result = provider.authenticate("nobody", "x", "10.0.0.1");
        assertThat(result).isInstanceOf(AuthResult.InvalidCredentials.class);
    }

    private void seedUser(String userId, String rawPassword) {
        userRepo.save(buildUser(userId, encoder.encode(rawPassword)));
    }

    static User buildUser(String userId, String hash) {
        User u = new User();
        u.setUserId(userId);
        u.setEmail(userId + "@test.lab");
        u.setFullName(userId);
        u.setPasswordHash(hash);
        return u;
    }
}
