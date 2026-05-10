package com.lab.edms.workflow;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "state", nullable = false, length = 20)
    private String state = "IN_PROGRESS";

    @Column(name = "current_step", nullable = false)
    private Integer currentStep = 1;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "started_by", nullable = false, updatable = false, length = 50)
    private String startedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by", length = 50)
    private String completedBy;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public Long getTemplateId() { return templateId; }
    public String getState() { return state; }
    public Integer getCurrentStep() { return currentStep; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public String getStartedBy() { return startedBy; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getCompletedBy() { return completedBy; }

    public void setVersionId(Long v) { this.versionId = v; }
    public void setTemplateId(Long v) { this.templateId = v; }
    public void setState(String v) { this.state = v; }
    public void setCurrentStep(Integer v) { this.currentStep = v; }
    public void setStartedBy(String v) { this.startedBy = v; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
    public void setCompletedBy(String v) { this.completedBy = v; }
}
