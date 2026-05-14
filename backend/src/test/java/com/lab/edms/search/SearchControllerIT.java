package com.lab.edms.search;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@DirtiesContext
class SearchControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void search_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "품질관리"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void search_withShortQuery_returns400() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "a"))
           .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void search_withValidQuery_returns200() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "품질관리"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content").isArray());
    }
}
