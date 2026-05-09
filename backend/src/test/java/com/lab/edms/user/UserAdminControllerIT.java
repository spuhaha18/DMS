package com.lab.edms.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.dto.*;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@Transactional
class UserAdminControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void postUsers_validBody_returns201() throws Exception {
        var req = new CreateUserRequest("ann_qa", "Ann", "ann@t.lab", "QA", null,
                "Initial!Pw1234", List.of("AUTHOR"), null, null);

        mvc.perform(post("/api/v1/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value("ann_qa"))
                .andExpect(jsonPath("$.force_change_pw").value(true))
                .andExpect(jsonPath("$.role_codes[0]").value("AUTHOR"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void postUsers_invalidUserIdPattern_returns400() throws Exception {
        var req = new CreateUserRequest("space user", "X", "x@t.lab", "QA", null,
                "Initial!Pw1234", List.of("AUTHOR"), null, null);

        mvc.perform(post("/api/v1/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"));
    }

    @Test
    @WithMockUser(username = "ann", roles = "AUTHOR")
    void postUsers_nonAdmin_returns403() throws Exception {
        var req = new CreateUserRequest("ann_qa", "Ann", "ann@t.lab", "QA", null,
                "Initial!Pw1234", List.of("AUTHOR"), null, null);

        mvc.perform(post("/api/v1/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void postDisableSelf_returns422_BR_USER_010() throws Exception {
        mvc.perform(post("/api/v1/admin/users/admin/disable").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"self-disable test\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("USER_005"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getExportCsv_returnsTextCsv() throws Exception {
        mvc.perform(get("/api/v1/admin/users/export?format=csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("user_id,full_name")));
    }
}
