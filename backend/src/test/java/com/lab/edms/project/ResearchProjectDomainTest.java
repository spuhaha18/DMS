package com.lab.edms.project;

import com.lab.edms.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchProjectDomainTest {

    private ResearchProject project;
    private ResearchProjectType type;
    private User actor;
    private final OffsetDateTime now = OffsetDateTime.now();

    @BeforeEach
    void setUp() {
        type = new ResearchProjectType();
        type.setTypeCode("CHEM_NEW");
        type.setTypeNameKr("화학신약");
        type.setRetentionYears(20);
        type.setPerpetual(false);

        actor = new User();

        project = new ResearchProject();
        project.setProjectCode("P-2026-001");
        project.setProjectName("신약 A 임상");
        project.setType(type);
        project.setCreatedBy(actor);
        project.setUpdatedBy(actor);
    }

    @Test
    void retentionStartDateIsNullWhenActive() {
        assertThat(project.getStatus()).isEqualTo(ResearchProjectStatus.ACTIVE);
        assertThat(project.retentionStartDate()).isNull();
    }

    @Test
    void retentionStartDateIsApprovalDateAfterApprove() {
        LocalDate approvalDate = LocalDate.of(2026, 6, 1);
        project.approve(approvalDate, actor, now);

        assertThat(project.getStatus()).isEqualTo(ResearchProjectStatus.APPROVED);
        assertThat(project.retentionStartDate()).isEqualTo(approvalDate);
        assertThat(project.getApprovalDate()).isEqualTo(approvalDate);
    }

    @Test
    void retentionStartDateIsTerminationDateAfterTerminate() {
        LocalDate terminationDate = LocalDate.of(2026, 12, 31);
        project.terminate(terminationDate, actor, now);

        assertThat(project.getStatus()).isEqualTo(ResearchProjectStatus.TERMINATED);
        assertThat(project.retentionStartDate()).isEqualTo(terminationDate);
        assertThat(project.getTerminationDate()).isEqualTo(terminationDate);
    }

    @Test
    void rejectsDoubleApprove() {
        project.approve(LocalDate.of(2026, 6, 1), actor, now);

        assertThatThrownBy(() ->
            project.approve(LocalDate.of(2026, 7, 1), actor, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void rejectsTerminateAfterApprove() {
        project.approve(LocalDate.of(2026, 6, 1), actor, now);

        assertThatThrownBy(() ->
            project.terminate(LocalDate.of(2026, 12, 31), actor, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }
}
