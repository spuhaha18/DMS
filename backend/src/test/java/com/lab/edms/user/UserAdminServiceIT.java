package com.lab.edms.user;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.common.ConflictException;
import com.lab.edms.common.UnprocessableEntityException;
import com.lab.edms.user.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class UserAdminServiceIT {

    @Autowired UserAdminService svc;
    @Autowired UserRepository userRepo;

    @Test
    void create_assignsRoles_setsForceChangePw_storesBcryptHash() {
        UserDto created = svc.create(new CreateUserRequest(
                "ann_qa", "Ann QA", "ann@lab.test", "QA", "Officer",
                "Initial!Pw1234", List.of("AUTHOR", "READER"), null, null), "admin", "10.0.0.1");

        assertThat(created.userId()).isEqualTo("ann_qa");
        assertThat(created.forceChangePw()).isTrue();
        assertThat(created.roleCodes()).containsExactly("AUTHOR", "READER");

        User stored = userRepo.findByUserId("ann_qa").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    void create_duplicateUserId_throwsConflict() {
        svc.create(new CreateUserRequest("dupe", "D", "d1@t", "QC", null, "Initial!Pw1234",
                List.of("AUTHOR"), null, null), "admin", "10.0.0.1");

        assertThatThrownBy(() -> svc.create(new CreateUserRequest(
                "dupe", "D2", "d2@t", "QC", null, "Initial!Pw1234",
                List.of("AUTHOR"), null, null), "admin", "10.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_invalidRole_throwsUnprocessable() {
        assertThatThrownBy(() -> svc.create(new CreateUserRequest(
                "bad", "B", "b@t", "QC", null, "Initial!Pw1234",
                List.of("NOPE"), null, null), "admin", "10.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void disable_self_throwsUnprocessable_BR_USER_010() {
        assertThatThrownBy(() -> svc.disable("admin", "Self disable test", "admin", "10.0.0.1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("self");
    }
}
