package com.lab.edms.user;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class AccessReviewServiceIT {

    @Autowired AccessReviewService svc;

    @Test
    void exportCsv_includesHeader_and_admin_row_with_roles() {
        String csv = svc.exportCsv();

        assertThat(csv).startsWith(
                "user_id,full_name,email,department,title,status,valid_from,valid_until,last_login_at,role_codes\n");
        assertThat(csv).contains("admin,");
        assertThat(csv).contains(",ADMIN");
    }
}
