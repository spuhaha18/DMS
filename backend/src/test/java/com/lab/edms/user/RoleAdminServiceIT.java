package com.lab.edms.user;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.user.dto.UpdateRoleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class RoleAdminServiceIT {

    @Autowired RoleAdminService svc;
    @Autowired RoleRepository roleRepo;

    @Test
    void list_returnsEightSeededSystemRoles() {
        assertThat(svc.list()).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void update_changesNameAndDescription_butLeavesRoleCodeImmutable() {
        Role admin = roleRepo.findByRoleCode("ADMIN").orElseThrow();
        Long pk = admin.getId();

        svc.update(pk, new UpdateRoleRequest("관리자(수정)", "Updated description"),
                "admin", "10.0.0.1");

        Role reloaded = roleRepo.findById(pk).orElseThrow();
        assertThat(reloaded.getRoleCode()).isEqualTo("ADMIN");
        assertThat(reloaded.getRoleName()).isEqualTo("관리자(수정)");
        assertThat(reloaded.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void update_unknownId_throwsNotFound() {
        assertThatThrownBy(() ->
                svc.update(999_999L, new UpdateRoleRequest("x", "y"), "admin", "10.0.0.1"))
                .isInstanceOf(NotFoundException.class);
    }
}
