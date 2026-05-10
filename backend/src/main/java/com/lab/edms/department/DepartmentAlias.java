package com.lab.edms.department;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "department_aliases")
public class DepartmentAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    @Column(name = "alias_name", nullable = false, length = 100)
    private String aliasName;

    @Column(name = "locale", length = 8)
    private String locale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getDeptId() { return deptId; }
    public String getAliasName() { return aliasName; }
    public String getLocale() { return locale; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setDeptId(Long v) { this.deptId = v; }
    public void setAliasName(String v) { this.aliasName = v; }
    public void setLocale(String v) { this.locale = v; }
}
