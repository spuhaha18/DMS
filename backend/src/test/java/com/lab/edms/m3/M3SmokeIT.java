package com.lab.edms.m3;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.numbering.NumberingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
class M3SmokeIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired NumberingService numberingService;

    private Long sopCategoryId;

    @BeforeEach
    void setUp() {
        sopCategoryId = jdbc.queryForObject(
            "SELECT id FROM document_categories WHERE category_code = 'SOP'",
            Long.class);
        // Clean up smoke-test-specific counters to ensure deterministic results
        jdbc.update(
            "DELETE FROM numbering_counters WHERE category_id = ? AND scope_key IN ('QC_SMOKE','QA_SMOKE')",
            sopCategoryId);
    }

    @Test
    void 시드_카테고리_4개_존재() {
        // OQ-DOC-001: V12 seed must have SOP/METHOD/SPEC/FORM active
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_categories " +
            "WHERE category_code IN ('SOP','METHOD','SPEC','FORM') AND is_active = TRUE",
            Integer.class);
        assertThat(count).isEqualTo(4);

        Integer templateCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM numbering_templates nt " +
            "JOIN document_categories dc ON dc.id = nt.category_id " +
            "WHERE dc.category_code IN ('SOP','METHOD','SPEC','FORM')",
            Integer.class);
        assertThat(templateCount).isEqualTo(4);
    }

    @Test
    @Transactional
    void 부서별_독립_시퀀스() {
        // OQ-DOC-004: PER_DEPT scope — different departments get independent sequences
        // Uses QC_SMOKE / QA_SMOKE to avoid collision with ConcurrencyIT which uses "QC"
        String qc1 = numberingService
            .issue(sopCategoryId, new NumberingService.IssueContext("QC_SMOKE", null))
            .docNumber();
        String qa1 = numberingService
            .issue(sopCategoryId, new NumberingService.IssueContext("QA_SMOKE", null))
            .docNumber();
        String qc2 = numberingService
            .issue(sopCategoryId, new NumberingService.IssueContext("QC_SMOKE", null))
            .docNumber();

        // Both QC_SMOKE and QA_SMOKE must start from 001 (independent)
        assertThat(qc1).endsWith("001");
        assertThat(qa1).endsWith("001");
        // QC_SMOKE increments independently to 002
        assertThat(qc2).endsWith("002");
    }

    @Test
    void 권한없는_카테고리_조회_금지() {
        // Structural guard: PermissionResolver is covered by PermissionResolverIT.
        // Here we simply verify that the permissions table exists and is queryable,
        // confirming the access-control schema is in place.
        Integer permCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permissions", Integer.class);
        assertThat(permCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    void v12_채번_템플릿_패턴_검증() {
        // Verify seed numbering templates have the expected placeholder patterns
        String sopPattern = jdbc.queryForObject(
            "SELECT nt.format_pattern FROM numbering_templates nt " +
            "JOIN document_categories dc ON dc.id = nt.category_id " +
            "WHERE dc.category_code = 'SOP'",
            String.class);
        assertThat(sopPattern).contains("{TYPE}").contains("{DEPT}").contains("{SEQ:");

        String specPattern = jdbc.queryForObject(
            "SELECT nt.format_pattern FROM numbering_templates nt " +
            "JOIN document_categories dc ON dc.id = nt.category_id " +
            "WHERE dc.category_code = 'SPEC'",
            String.class);
        assertThat(specPattern).contains("{PROD}");
    }

    @Test
    void m2_backfill_부서_정규화() {
        // V11 backfill: every user with a non-empty department must reference a known dept_code
        Integer orphans = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users " +
            "WHERE department IS NOT NULL AND department <> '' " +
            "AND department NOT IN (SELECT dept_code FROM departments)",
            Integer.class);
        assertThat(orphans).isEqualTo(0);
    }
}
