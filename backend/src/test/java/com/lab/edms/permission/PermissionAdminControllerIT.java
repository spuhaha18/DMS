package com.lab.edms.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.permission.dto.UpsertPermissionRequest;
import com.lab.edms.user.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@Transactional
class PermissionAdminControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void putPermissions_upsertsAndReturnsRow() throws Exception {
        Long roleId = roleRepo.findByRoleCode("AUTHOR").orElseThrow().getId();
        DocumentCategory c = new DocumentCategory();
        c.setCategoryCode("CC1"); c.setCategoryName("CC1"); c.setActive(true);
        catRepo.save(c);

        var req = new UpsertPermissionRequest(roleId, c.getId(), "QC",
                true, false, true, true, false, false, false);

        mvc.perform(put("/api/v1/admin/permissions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role_code").value("AUTHOR"))
                .andExpect(jsonPath("$.can_create").value(true));
    }

    @Test
    @WithMockUser(username = "ann", roles = "AUTHOR")
    void getPermissions_nonAdmin_returns403() throws Exception {
        mvc.perform(get("/api/v1/admin/permissions"))
                .andExpect(status().isForbidden());
    }
}
