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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class AuditorExpirySessionSchedulerIT {

    @Autowired AuditorExpirySessionScheduler scheduler;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;

    @Test
    void runExpiryTick_disablesUserWhoseValidUntilHasPassed() {
        User u = LocalAuthProviderIT.buildUser("expired-aud", encoder.encode("Pw!2026Pwd"));
        u.setValidUntil(LocalDate.now().minusDays(1));
        u.setStatus(UserStatus.ACTIVE);
        userRepo.save(u);

        scheduler.runExpiryTick();

        assertThat(userRepo.findByUserId("expired-aud").orElseThrow().getStatus())
                .isEqualTo(UserStatus.DISABLED);
    }
}
