package com.lab.edms.user;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class UserRepositoryIT {

    @Autowired UserRepository userRepo;

    @Test
    void findAll_filterByStatusAndDepartment_returnsExpectedPage() {
        userRepo.save(makeUser("u-active-qc", UserStatus.ACTIVE, "QC"));
        userRepo.save(makeUser("u-disabled-qc", UserStatus.DISABLED, "QC"));
        userRepo.save(makeUser("u-active-ra", UserStatus.ACTIVE, "RA"));

        Page<User> page = userRepo.searchAdmin(UserStatus.ACTIVE, "QC", PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(User::getUserId).containsExactly("u-active-qc");
    }

    @Test
    void existsByEmail_caseInsensitive_findsDuplicate() {
        User u = makeUser("u-email", UserStatus.ACTIVE, "QC");
        u.setEmail("Mixed@Case.Test");
        userRepo.save(u);
        assertThat(userRepo.existsByEmailIgnoreCase("mixed@case.test")).isTrue();
    }

    private User makeUser(String userId, UserStatus s, String dept) {
        User u = new User();
        u.setUserId(userId);
        u.setEmail(userId + "@t.lab");
        u.setFullName(userId);
        u.setDepartment(dept);
        u.setStatus(s);
        return u;
    }
}
