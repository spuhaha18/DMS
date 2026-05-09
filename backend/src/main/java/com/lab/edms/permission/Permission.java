package com.lab.edms.permission;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "can_view", nullable = false)        private boolean canView;
    @Column(name = "can_download", nullable = false)    private boolean canDownload;
    @Column(name = "can_create", nullable = false)      private boolean canCreate;
    @Column(name = "can_edit_draft", nullable = false)  private boolean canEditDraft;
    @Column(name = "can_review", nullable = false)      private boolean canReview;
    @Column(name = "can_approve", nullable = false)     private boolean canApprove;
    @Column(name = "can_retire", nullable = false)      private boolean canRetire;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getRoleId() { return roleId; }
    public Long getCategoryId() { return categoryId; }
    public String getDepartment() { return department; }
    public boolean isCanView() { return canView; }
    public boolean isCanDownload() { return canDownload; }
    public boolean isCanCreate() { return canCreate; }
    public boolean isCanEditDraft() { return canEditDraft; }
    public boolean isCanReview() { return canReview; }
    public boolean isCanApprove() { return canApprove; }
    public boolean isCanRetire() { return canRetire; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setRoleId(Long v) { this.roleId = v; }
    public void setCategoryId(Long v) { this.categoryId = v; }
    public void setDepartment(String v) { this.department = v; }
    public void setCanView(boolean v) { this.canView = v; }
    public void setCanDownload(boolean v) { this.canDownload = v; }
    public void setCanCreate(boolean v) { this.canCreate = v; }
    public void setCanEditDraft(boolean v) { this.canEditDraft = v; }
    public void setCanReview(boolean v) { this.canReview = v; }
    public void setCanApprove(boolean v) { this.canApprove = v; }
    public void setCanRetire(boolean v) { this.canRetire = v; }
}
