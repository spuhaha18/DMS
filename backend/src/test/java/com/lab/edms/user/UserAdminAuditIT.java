package com.lab.edms.user;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.dto.CreateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class UserAdminAuditIT {

    @Autowired UserAdminService svc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void create_writesUserCreatedAndRoleAssignedAuditRows() {
        svc.create(new CreateUserRequest("ann_aud", "Ann", "ann_aud@t.lab", "QA", null,
                "Initial!Pw1234", List.of("AUTHOR", "READER"), null, null), "admin", "10.0.0.1");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT action, actor_user_id FROM audit_logs ORDER BY id DESC LIMIT 10");

        assertThat(rows).extracting(r -> r.get("action").toString())
                .contains("USER_CREATED", "ROLE_ASSIGNED");
    }
}
