package com.lab.edms.workflow;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_template_steps")
public class WorkflowTemplateStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_type", nullable = false, length = 20)
    private String stepType;  // 'REVIEW' | 'APPROVAL'

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(name = "min_signers", nullable = false)
    private Integer minSigners = 1;

    @Column(name = "parallel", nullable = false)
    private boolean parallel = false;

    @Column(name = "auto_assign", nullable = false)
    private boolean autoAssign = false;

    @Column(name = "qa_required", nullable = false)
    private boolean qaRequired = false;

    public Long getId() { return id; }
    public Long getTemplateId() { return templateId; }
    public Integer getStepOrder() { return stepOrder; }
    public String getStepType() { return stepType; }
    public String getRoleCode() { return roleCode; }
    public Integer getMinSigners() { return minSigners; }
    public boolean isParallel() { return parallel; }
    public boolean isAutoAssign() { return autoAssign; }
    public boolean isQaRequired() { return qaRequired; }

    public void setTemplateId(Long v) { this.templateId = v; }
    public void setStepOrder(Integer v) { this.stepOrder = v; }
    public void setStepType(String v) { this.stepType = v; }
    public void setRoleCode(String v) { this.roleCode = v; }
    public void setMinSigners(Integer v) { this.minSigners = v; }
    public void setParallel(boolean v) { this.parallel = v; }
    public void setAutoAssign(boolean v) { this.autoAssign = v; }
    public void setQaRequired(boolean v) { this.qaRequired = v; }
}
