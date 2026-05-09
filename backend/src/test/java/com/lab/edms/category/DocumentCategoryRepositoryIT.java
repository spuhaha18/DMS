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
        DocumentCategory c = new DocumentCategory();
        c.setCategoryCode("SOP");
        c.setCategoryName("Standard Operating Procedure");
        c.setReviewPeriodMonths(24);
        c.setQaMandatory(true);
        c.setActive(true);
        repo.save(c);

        assertThat(repo.findByCategoryCode("SOP")).isPresent();
    }
}
