package com.lab.edms.category;

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
class DocumentCategoryRepositoryIT {

    @Autowired DocumentCategoryRepository repo;

    @Test
    void save_andFindByCategoryCode_roundTrips() {
        // V12 seed inserts SOP/METHOD/SPEC/FORM — use a test-only code to avoid conflict
        DocumentCategory c = new DocumentCategory();
        c.setCategoryCode("TEST_CAT");
        c.setCategoryName("Test Category");
        c.setReviewPeriodMonths(24);
        c.setQaMandatory(false);
        c.setActive(true);
        repo.save(c);

        assertThat(repo.findByCategoryCode("TEST_CAT")).isPresent();
    }
}
