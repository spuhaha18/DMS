package com.lab.edms.document;

import com.lab.edms.project.ResearchProject;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_number", nullable = false, unique = true, length = 50)
    private String docNumber;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "department", nullable = false, length = 50)
    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_code")
    private ResearchProject project;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    // V21: PDF 파이프라인 상태기계
    @Column(name = "pdf_status", length = 32)
    private String pdfStatus;

    @Column(name = "pdf_status_updated_at")
    private Instant pdfStatusUpdatedAt;

    @Column(name = "pdf_status_reason", length = 64)
    private String pdfStatusReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getDocNumber() { return docNumber; }
    public Long getCategoryId() { return categoryId; }
    public String getDepartment() { return department; }
    public ResearchProject getProject() { return project; }
    public String getProjectCode() { return project != null ? project.getProjectCode() : null; }
    public String getTitle() { return title; }
    public Long getOwnerId() { return ownerId; }
    public boolean isConfidential() { return confidential; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public String getPdfStatus() { return pdfStatus; }
    public Instant getPdfStatusUpdatedAt() { return pdfStatusUpdatedAt; }
    public String getPdfStatusReason() { return pdfStatusReason; }

    public void setDocNumber(String v) { this.docNumber = v; }
    public void setCategoryId(Long v) { this.categoryId = v; }
    public void setDepartment(String v) { this.department = v; }
    public void setProject(ResearchProject v) { this.project = v; }
    public void setTitle(String v) { this.title = v; }
    public void setOwnerId(Long v) { this.ownerId = v; }
    public void setConfidential(boolean v) { this.confidential = v; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public void setPdfStatus(String v) { this.pdfStatus = v; }
    public void setPdfStatusUpdatedAt(Instant v) { this.pdfStatusUpdatedAt = v; }
    public void setPdfStatusReason(String v) { this.pdfStatusReason = v; }
}
