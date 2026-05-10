package com.lab.edms.numbering;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "numbering_templates")
public class NumberingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false, unique = true)
    private Long categoryId;

    @Column(name = "format_pattern", nullable = false, length = 200)
    private String formatPattern;

    @Column(name = "counter_scope", nullable = false, length = 20)
    private String counterScope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getFormatPattern() { return formatPattern; }
    public String getCounterScope() { return counterScope; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }

    public void setCategoryId(Long v) { this.categoryId = v; }
    public void setFormatPattern(String v) { this.formatPattern = v; }
    public void setCounterScope(String v) { this.counterScope = v; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }
}
