package com.lab.edms.auth;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class AuthServiceIT {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired JdbcTemplate primary;

    @Test
    void successfulLogin_writesAuditLog_USER_LOGIN_SUCCESS() {
        seedUser("erin", "Correct!Pass1");

        AuthResult result = authService.login("erin", "Correct!Pass1", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        Map<String, Object> log = primary.queryForMap(
                "SELECT actor_user_id, action FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(log.get("actor_user_id")).isEqualTo("erin");
        assertThat(log.get("action")).isEqualTo("USER_LOGIN_SUCCESS");
    }

    @Test
    void failedLogin_writesAuditLog_USER_LOGIN_FAIL() {
        seedUser("frank", "Correct!Pass1");

        authService.login("frank", "wrong", "10.0.0.1");

        Map<String, Object> log = primary.queryForMap(
                "SELECT action FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(log.get("action")).isEqualTo("USER_LOGIN_FAIL");
    }

    @Test
    void fifthFailedLogin_writesUSER_LOCKED_event() {
        seedUser("grace", "Correct!Pass1");

        for (int i = 0; i < 5; i++) authService.login("grace", "wrong", "10.0.0.1");

        long lockedCount = primary.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE actor_user_id='grace' AND action='USER_LOCKED'",
                Long.class);
        assertThat(lockedCount).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void logout_writesAuditLog_USER_LOGOUT() {
        seedUser("henry", "Correct!Pass1");
        authService.login("henry", "Correct!Pass1", "10.0.0.1");

        authService.logout("henry", "10.0.0.1");

        Map<String, Object> log = primary.queryForMap(
                "SELECT action FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(log.get("action")).isEqualTo("USER_LOGOUT");
    }

    private void seedUser(String userId, String rawPassword) {
        User u = new LocalAuthProviderIT.TestUser(userId, encoder.encode(rawPassword));
        userRepo.save(u);
    }
}
