package com.lab.edms.auth;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class LocalAuthProviderValidityIT {

    @Autowired LocalAuthProvider provider;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;

    @Test
    void beforeValidFrom_returnsAccountDisabled() {
        User u = seed("future-user");
        u.setValidFrom(LocalDate.now().plusDays(7));
        userRepo.save(u);

        AuthResult r = provider.authenticate("future-user", "Pw!2026Pwd", "1.1.1.1");
        assertThat(r).isInstanceOf(AuthResult.AccountDisabled.class);
    }

    @Test
    void afterValidUntil_returnsAccountDisabled() {
        User u = seed("expired-user");
        u.setValidUntil(LocalDate.now().minusDays(1));
        userRepo.save(u);

        AuthResult r = provider.authenticate("expired-user", "Pw!2026Pwd", "1.1.1.1");
        assertThat(r).isInstanceOf(AuthResult.AccountDisabled.class);
    }

    @Test
    void successfulAuth_updatesLastLoginAt() {
        User u = seed("ll-user");
        userRepo.save(u);

        provider.authenticate("ll-user", "Pw!2026Pwd", "1.1.1.1");

        User reloaded = userRepo.findByUserId("ll-user").orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    private User seed(String userId) {
        User u = LocalAuthProviderIT.buildUser(userId, encoder.encode("Pw!2026Pwd"));
        return userRepo.save(u);
    }
}
