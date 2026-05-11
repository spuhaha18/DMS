package com.lab.edms.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@DirtiesContext
class AuditChecksControllerIT {

    @DynamicPropertySource
    static void minioProps(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",       TestcontainersConfig.MINIO::getS3URL);
        registry.add("minio.access-key",     TestcontainersConfig.MINIO::getUserName);
        registry.add("minio.secret-key",     TestcontainersConfig.MINIO::getPassword);
        registry.add("minio.bucket-anchors", () -> "test-edms-audit-anchors");
    }

    @Autowired MockMvc mockMvc;
    @Autowired AuditService auditService;
    @Autowired AnchorService anchorService;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE audit_checkpoints RESTART IDENTITY");

        ZoneId kst = ZoneId.of("Asia/Seoul");
        LocalDate yesterday = LocalDate.now(kst).minusDays(1);
        LocalDate dayBefore = yesterday.minusDays(1);
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1",
                dayBefore.atTime(12, 0).atZone(kst).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()));
        auditService.log(new AuditEvent("b", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "2", null, null, null, "10.0.0.1",
                yesterday.atTime(12, 0).atZone(kst).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()));
        anchorService.buildAndStore(dayBefore);
        anchorService.buildAndStore(yesterday);
    }

    /** anonymous 호출 → 401 */
    @Test
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/checkpoints"))
                .andExpect(status().isUnauthorized());
    }

    /** 일반 사용자(non-admin/auditor) → 403 */
    @Test
    @WithMockUser(username = "alice", authorities = {"ROLE_AUTHOR"})
    void nonPrivileged_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/checkpoints"))
                .andExpect(status().isForbidden());
    }

    /** OQ-AUD-016: clean verify → valid=true */
    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    void verify_clean_returnsValidTrue() throws Exception {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        LocalDate yesterday = LocalDate.now(kst).minusDays(1);
        LocalDate dayBefore = yesterday.minusDays(1);

        String body = String.format("{\"from_date\":\"%s\",\"to_date\":\"%s\"}", dayBefore, yesterday);

        mockMvc.perform(post("/api/v1/audit-logs/checkpoints/verify")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.checked_records").value(2))
                .andExpect(jsonPath("$.first_broken_id").doesNotExist());
    }

    /** AUDITOR 도 접근 가능 */
    @Test
    @WithMockUser(username = "audit-officer", authorities = {"ROLE_AUDITOR"})
    void auditor_canList() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/checkpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /** GET 범위 필터 동작 */
    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    void list_filterByRange() throws Exception {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        LocalDate yesterday = LocalDate.now(kst).minusDays(1);

        mockMvc.perform(get("/api/v1/audit-logs/checkpoints")
                        .param("from", yesterday.toString())
                        .param("to",   yesterday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
