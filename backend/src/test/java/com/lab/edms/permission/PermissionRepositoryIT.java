package com.lab.edms.permission;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.user.Role;
import com.lab.edms.user.RoleRepository;
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
class PermissionRepositoryIT {

    @Autowired PermissionRepository permRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;

    @Test
    void findByRoleAndCategoryAndDepartment_findsExactRow() {
        Role role = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
        DocumentCategory cat = new DocumentCategory();
        cat.setCategoryCode("TST");
        cat.setCategoryName("Test");
        cat.setQaMandatory(false);
        cat.setActive(true);
        catRepo.save(cat);

        Permission p = new Permission();
        p.setRoleId(role.getId());
        p.setCategoryId(cat.getId());
        p.setDepartment("QC");
        p.setCanView(true);
        permRepo.save(p);

        assertThat(permRepo.findByRoleIdAndCategoryIdAndDepartment(role.getId(), cat.getId(), "QC"))
                .isPresent();
        assertThat(permRepo.findByRoleIdAndCategoryIdAndDepartment(role.getId(), cat.getId(), null))
                .isEmpty();
    }

    @Test
    void findOrgWide_matchesNullDepartmentRow() {
        Role role = roleRepo.findByRoleCode("ADMIN").orElseThrow();
        DocumentCategory cat = new DocumentCategory();
        cat.setCategoryCode("ORG");
        cat.setCategoryName("Org");
        cat.setQaMandatory(false);
        cat.setActive(true);
        catRepo.save(cat);

        Permission p = new Permission();
        p.setRoleId(role.getId());
        p.setCategoryId(cat.getId());
        p.setDepartment(null);
        p.setCanView(true);
        permRepo.save(p);

        assertThat(permRepo.findByRoleIdAndCategoryIdAndDepartmentIsNull(role.getId(), cat.getId()))
                .isPresent();
    }
}
