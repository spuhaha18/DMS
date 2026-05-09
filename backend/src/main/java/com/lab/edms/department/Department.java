package com.lab.edms.department;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import java.time.OffsetDateTime;

@Entity
@Audited
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dept_code", nullable = false, unique = true, length = 50)
    private String deptCode;

    @Column(name = "primary_name", nullable = false, length = 100)
    private String primaryName;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "source", nullable = false, length = 20)
    private String source = "INTERNAL";

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public String getDeptCode() { return deptCode; }
    public String getPrimaryName() { return primaryName; }
    public String getExternalId() { return externalId; }
    public String getSource() { return source; }
    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }

    public void setDeptCode(String v) { this.deptCode = v; }
    public void setPrimaryName(String v) { this.primaryName = v; }
    public void setExternalId(String v) { this.externalId = v; }
    public void setSource(String v) { this.source = v; }
    public void setLastSyncedAt(OffsetDateTime v) { this.lastSyncedAt = v; }
    public void setActive(boolean v) { this.active = v; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }
}
