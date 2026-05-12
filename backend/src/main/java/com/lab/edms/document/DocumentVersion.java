package com.lab.edms.document;

import com.lab.edms.lifecycle.DocumentState;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_versions")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "revision")
    private Integer revision;

    @Column(name = "state", nullable = false, length = 30)
    private String state = "DRAFT";

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "reason_for_change")
    private String reasonForChange;

    @Column(name = "source_file_key", length = 500)
    private String sourceFileKey;

    @Column(name = "pdf_file_key", length = 500)
    private String pdfFileKey;

    @Column(name = "pdf_status", nullable = false, length = 20)
    private String pdfStatus = "PENDING";

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public Integer getRevision() { return revision; }
    public String getState() { return state; }
    public String getTitle() { return title; }
    public String getChangeSummary() { return changeSummary; }
    public String getReasonForChange() { return reasonForChange; }
    public String getSourceFileKey() { return sourceFileKey; }
    public String getPdfFileKey() { return pdfFileKey; }
    public String getPdfStatus() { return pdfStatus; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }

    public void setDocumentId(Long v) { this.documentId = v; }
    public void setRevision(Integer v) { this.revision = v; }
    public void setState(String v) { this.state = v; }
    public void setTitle(String v) { this.title = v; }
    public void setChangeSummary(String v) { this.changeSummary = v; }
    public void setReasonForChange(String v) { this.reasonForChange = v; }
    public void setSourceFileKey(String v) { this.sourceFileKey = v; }
    public void setPdfFileKey(String v) { this.pdfFileKey = v; }
    public void setPdfStatus(String v) { this.pdfStatus = v; }
    public void setEffectiveDate(LocalDate v) { this.effectiveDate = v; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }

    public DocumentState getStateAsEnum() {
        return DocumentState.parse(this.state);
    }
}
