package com.lab.edms.category;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_categories")
public class DocumentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_code", nullable = false, unique = true, length = 20)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "description")
    private String description;

    @Column(name = "review_period_months", nullable = false)
    private int reviewPeriodMonths = 24;

    @Column(name = "qa_mandatory", nullable = false)
    private boolean qaMandatory;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getCategoryCode() { return categoryCode; }
    public String getCategoryName() { return categoryName; }
    public String getDescription() { return description; }
    public int getReviewPeriodMonths() { return reviewPeriodMonths; }
    public boolean isQaMandatory() { return qaMandatory; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCategoryCode(String v) { this.categoryCode = v; }
    public void setCategoryName(String v) { this.categoryName = v; }
    public void setDescription(String v) { this.description = v; }
    public void setReviewPeriodMonths(int v) { this.reviewPeriodMonths = v; }
    public void setQaMandatory(boolean v) { this.qaMandatory = v; }
    public void setActive(boolean v) { this.active = v; }
}
