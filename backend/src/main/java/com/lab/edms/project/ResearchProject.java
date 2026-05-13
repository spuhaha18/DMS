package com.lab.edms.project;

import com.lab.edms.user.User;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "research_projects")
@Audited
public class ResearchProject {

    @Id
    @Column(name = "project_code", length = 50)
    private String projectCode;

    @Column(name = "project_name", nullable = false, length = 500)
    private String projectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", nullable = false)
    private ResearchProjectType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResearchProjectStatus status = ResearchProjectStatus.ACTIVE;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pm_user_id")
    private User pmUser;

    @Column(name = "department_code", length = 50)
    private String departmentCode;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Returns the date from which retention is calculated.
     * ACTIVE → null (retention start not yet determined; temporary 30y lock applies).
     * APPROVED → approvalDate.
     * TERMINATED → terminationDate.
     */
    public LocalDate retentionStartDate() {
        return switch (status) {
            case APPROVED -> approvalDate;
            case TERMINATED -> terminationDate;
            case ACTIVE -> null;
        };
    }

    public void approve(LocalDate date, User actor, OffsetDateTime now) {
        if (status != ResearchProjectStatus.ACTIVE) {
            throw new IllegalStateException(
                "Project " + projectCode + " cannot be approved from status " + status);
        }
        this.approvalDate = date;
        this.status = ResearchProjectStatus.APPROVED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void terminate(LocalDate date, User actor, OffsetDateTime now) {
        if (status != ResearchProjectStatus.ACTIVE) {
            throw new IllegalStateException(
                "Project " + projectCode + " cannot be terminated from status " + status);
        }
        this.terminationDate = date;
        this.status = ResearchProjectStatus.TERMINATED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public String getProjectCode() { return projectCode; }
    public String getProjectName() { return projectName; }
    public ResearchProjectType getType() { return type; }
    public ResearchProjectStatus getStatus() { return status; }
    public LocalDate getApprovalDate() { return approvalDate; }
    public LocalDate getTerminationDate() { return terminationDate; }
    public User getPmUser() { return pmUser; }
    public String getDepartmentCode() { return departmentCode; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public User getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public User getUpdatedBy() { return updatedBy; }

    public void setProjectCode(String v) { this.projectCode = v; }
    public void setProjectName(String v) { this.projectName = v; }
    public void setType(ResearchProjectType v) { this.type = v; }
    public void setStatus(ResearchProjectStatus v) { this.status = v; }
    public void setPmUser(User v) { this.pmUser = v; }
    public void setDepartmentCode(String v) { this.departmentCode = v; }
    public void setDescription(String v) { this.description = v; }
    public void setCreatedBy(User v) { this.createdBy = v; }
    public void setUpdatedBy(User v) { this.updatedBy = v; }
}
