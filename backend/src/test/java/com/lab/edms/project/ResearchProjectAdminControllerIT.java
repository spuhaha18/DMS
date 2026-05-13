package com.lab.edms.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import com.lab.edms.project.dto.ApproveProjectRequest;
import com.lab.edms.project.dto.CreateProjectRequest;
import com.lab.edms.project.dto.TerminateProjectRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@Transactional
class ResearchProjectAdminControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ResearchProjectRepository projectRepo;
    @Autowired ResearchProjectTypeRepository typeRepo;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listProjects_asAdmin_returns200() throws Exception {
        mvc.perform(get("/api/v1/admin/research-projects"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createProject_asAdmin_returns201() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest(
                "P-IT-001", "통합테스트 프로젝트", "CHEM_NEW");

        mvc.perform(post("/api/v1/admin/research-projects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectCode").value("P-IT-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void approveProject_asAdmin_returns200() throws Exception {
        // 먼저 프로젝트 생성
        CreateProjectRequest createReq = new CreateProjectRequest(
                "P-IT-002", "승인 테스트 프로젝트", "CHEM_NEW");
        mvc.perform(post("/api/v1/admin/research-projects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // 프로젝트 승인
        ApproveProjectRequest approveReq = new ApproveProjectRequest(LocalDate.of(2026, 6, 1));
        mvc.perform(post("/api/v1/admin/research-projects/P-IT-002/approve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvalDate").value("2026-06-01"));

        // 파일이 없으므로 outbox 적재 건수는 0이어야 함
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM retention_extension_outbox WHERE project_code = ?",
                Integer.class, "P-IT-002");
        assertThat(outboxCount).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void terminateProject_asAdmin_returns200() throws Exception {
        // 먼저 프로젝트 생성
        CreateProjectRequest createReq = new CreateProjectRequest(
                "P-IT-003", "종료 테스트 프로젝트", "CHEM_NEW");
        mvc.perform(post("/api/v1/admin/research-projects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // 프로젝트 종료
        TerminateProjectRequest terminateReq = new TerminateProjectRequest(LocalDate.of(2026, 12, 31));
        mvc.perform(post("/api/v1/admin/research-projects/P-IT-003/terminate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(terminateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                .andExpect(jsonPath("$.terminationDate").value("2026-12-31"));
    }

    @Test
    @WithMockUser(username = "reader", roles = "READER")
    void listProjects_asReader_returns403() throws Exception {
        mvc.perform(get("/api/v1/admin/research-projects"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "qa_user", roles = "QA")
    void listProjectTypes_asQA_returns200() throws Exception {
        mvc.perform(get("/api/v1/admin/research-project-types"))
                .andExpect(status().isOk());
    }
}
