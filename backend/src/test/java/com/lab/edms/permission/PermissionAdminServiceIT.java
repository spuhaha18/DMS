package com.lab.edms.permission;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.permission.dto.PermissionDto;
import com.lab.edms.permission.dto.UpsertPermissionRequest;
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
class PermissionAdminServiceIT {

    @Autowired PermissionAdminService svc;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;

    @Test
    void upsert_inserts_then_updates_sameRow() {
        Role author = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
        DocumentCategory cat = makeCat("PSV");

        PermissionDto inserted = svc.upsert(new UpsertPermissionRequest(
                author.getId(), cat.getId(), "QC",
                true, false, true, true, false, false, false), "admin", "1.1.1.1");

        PermissionDto updated = svc.upsert(new UpsertPermissionRequest(
                author.getId(), cat.getId(), "QC",
                true, true, true, true, false, false, false), "admin", "1.1.1.1");

        assertThat(updated.id()).isEqualTo(inserted.id());
        assertThat(updated.canDownload()).isTrue();
    }

    @Test
    void upsert_orgWide_nullDepartment_uniqueRow() {
        Role admin = roleRepo.findByRoleCode("ADMIN").orElseThrow();
        DocumentCategory cat = makeCat("ORG2");

        PermissionDto a = svc.upsert(new UpsertPermissionRequest(
                admin.getId(), cat.getId(), null,
                true, true, true, true, true, true, true), "admin", "1.1.1.1");
        PermissionDto b = svc.upsert(new UpsertPermissionRequest(
                admin.getId(), cat.getId(), null,
                false, false, false, false, false, false, false), "admin", "1.1.1.1");

        assertThat(a.id()).isEqualTo(b.id());
    }

    private DocumentCategory makeCat(String code) {
        DocumentCategory c = new DocumentCategory();
        c.setCategoryCode(code);
        c.setCategoryName(code + " name");
        c.setActive(true);
        catRepo.save(c);
        return c;
    }
}
