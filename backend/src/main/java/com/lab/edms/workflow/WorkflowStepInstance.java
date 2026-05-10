package com.lab.edms.workflow;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_step_instances")
public class WorkflowStepInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_type", nullable = false, length = 20)
    private String stepType;

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(name = "min_signers", nullable = false)
    private Integer minSigners;

    @Column(name = "parallel", nullable = false)
    private boolean parallel;

    @Column(name = "qa_required", nullable = false)
    private boolean qaRequired;

    @Column(name = "state", nullable = false, length = 20)
    private String state = "PENDING";

    @Convert(converter = AssigneeRefListConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assignees", columnDefinition = "jsonb")
    private List<AssigneeRef> assignees = new ArrayList<>();

    @Convert(converter = SignedRefListConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signed", columnDefinition = "jsonb")
    private List<SignedRef> signed = new ArrayList<>();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public Integer getStepOrder() { return stepOrder; }
    public String getStepType() { return stepType; }
    public String getRoleCode() { return roleCode; }
    public Integer getMinSigners() { return minSigners; }
    public boolean isParallel() { return parallel; }
    public boolean isQaRequired() { return qaRequired; }
    public String getState() { return state; }
    public List<AssigneeRef> getAssignees() { return assignees; }
    public List<SignedRef> getSigned() { return signed; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getRejectionReason() { return rejectionReason; }

    public void setWorkflowId(Long v) { this.workflowId = v; }
    public void setStepOrder(Integer v) { this.stepOrder = v; }
    public void setStepType(String v) { this.stepType = v; }
    public void setRoleCode(String v) { this.roleCode = v; }
    public void setMinSigners(Integer v) { this.minSigners = v; }
    public void setParallel(boolean v) { this.parallel = v; }
    public void setQaRequired(boolean v) { this.qaRequired = v; }
    public void setState(String v) { this.state = v; }
    public void setAssignees(List<AssigneeRef> v) { this.assignees = v; }
    public void setSigned(List<SignedRef> v) { this.signed = v; }
    public void setStartedAt(OffsetDateTime v) { this.startedAt = v; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
    public void setRejectionReason(String v) { this.rejectionReason = v; }
}
