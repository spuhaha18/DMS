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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class AccountUnlockSchedulerIT {

    @Autowired AccountUnlockScheduler scheduler;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;

    @Test
    void runUnlockTick_unlocksAccountsLockedMoreThan30MinutesAgo() {
        User u = LocalAuthProviderIT.buildUser("locked-old", encoder.encode("Pw!2026Pwd"));
        u.setStatus(UserStatus.LOCKED);
        u.setLockedAt(OffsetDateTime.now().minusMinutes(31));
        u.setFailedAttempts(5);
        userRepo.save(u);

        scheduler.runUnlockTick();

        User reloaded = userRepo.findByUserId("locked-old").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getFailedAttempts()).isZero();
        assertThat(reloaded.getLockedAt()).isNull();
    }

    @Test
    void runUnlockTick_doesNotUnlockRecentlyLockedAccounts() {
        User u = LocalAuthProviderIT.buildUser("locked-fresh", encoder.encode("Pw!2026Pwd"));
        u.setStatus(UserStatus.LOCKED);
        u.setLockedAt(OffsetDateTime.now().minusMinutes(5));
        u.setFailedAttempts(5);
        userRepo.save(u);

        scheduler.runUnlockTick();

        assertThat(userRepo.findByUserId("locked-fresh").orElseThrow().getStatus()).isEqualTo(UserStatus.LOCKED);
    }
}
