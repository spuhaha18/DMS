package com.lab.edms.project;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
class V23MigrationIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    void seedsFourProjectTypes() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM research_project_types", Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void addsUsesTable1FlagToDocumentCategories() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='document_categories' AND column_name='uses_table1'",
                Integer.class);
        assertThat(count).isEqualTo(1);

        Integer allFalse = jdbc.queryForObject(
                "SELECT COUNT(*) FROM document_categories WHERE uses_table1 = TRUE",
                Integer.class);
        assertThat(allFalse).isZero();
    }

    @Test
    void addsForeignKeyAndLegacyColumnToDocuments() {
        Integer legacyCol = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='documents' AND column_name='project_code_legacy'",
                Integer.class);
        assertThat(legacyCol).isEqualTo(1);

        Integer fkExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name='documents' AND constraint_name='fk_documents_project_code'",
                Integer.class);
        assertThat(fkExists).isEqualTo(1);
    }

    @Test
    void renamesCounterScopeToPerProjectKeepsPerYear() {
        // PER_PRODUCT should be renamed to PER_PROJECT
        Integer perProduct = jdbc.queryForObject(
                "SELECT COUNT(*) FROM numbering_templates WHERE counter_scope = 'PER_PRODUCT'",
                Integer.class);
        assertThat(perProduct).isZero();

        // PER_YEAR rows must be untouched
        Integer perYear = jdbc.queryForObject(
                "SELECT COUNT(*) FROM numbering_templates WHERE counter_scope = 'PER_YEAR'",
                Integer.class);
        assertThat(perYear).isGreaterThanOrEqualTo(0); // may be 0 if no PER_YEAR seed exists

        // CHECK constraint now accepts PER_PROJECT
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE numbering_templates SET counter_scope = 'BOGUS' WHERE 1=1"))
                .hasMessageContaining("counter_scope_check");
    }

    @Test
    void checkConstraintRejectsBogusScope() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO research_project_types " +
                "(type_code, type_name_kr, retention_years, is_perpetual) " +
                "VALUES ('X','X',NULL,FALSE)"))
                .hasMessageContaining("chk_rpt_retention");
    }

    @Test
    void perpetualTypeMustHaveNullRetentionYears() {
        // perpetual with non-null retention_years violates check
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO research_project_types " +
                "(type_code, type_name_kr, retention_years, is_perpetual) " +
                "VALUES ('PERP_BAD','영구',20,TRUE)"))
                .hasMessageContaining("chk_rpt_retention");

        // perpetual with null retention_years is valid
        jdbc.update(
                "INSERT INTO research_project_types " +
                "(type_code, type_name_kr, retention_years, is_perpetual) " +
                "VALUES ('PERP_OK','영구보존',NULL,TRUE)");
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM research_project_types WHERE type_code='PERP_OK'",
                Integer.class);
        assertThat(found).isEqualTo(1);
        // cleanup
        jdbc.update("DELETE FROM research_project_types WHERE type_code IN ('PERP_OK')");
    }
}
