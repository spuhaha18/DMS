package com.lab.edms.document;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_files")
public class DocumentFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "minio_bucket", nullable = false, length = 100)
    private String minioBucket;

    @Column(name = "minio_key", nullable = false, length = 500)
    private String minioKey;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    // V21: 단계별 RENDITION 누적
    @Column(name = "rendition_kind", length = 16)
    private String renditionKind;

    @Column(name = "step_number")
    private Integer stepNumber;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) uploadedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public String getFileType() { return fileType; }
    public String getMinioBucket() { return minioBucket; }
    public String getMinioKey() { return minioKey; }
    public String getFileName() { return fileName; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public String getContentType() { return contentType; }
    public String getSha256Hash() { return sha256Hash; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public Long getUploadedBy() { return uploadedBy; }
    public String getRenditionKind() { return renditionKind; }
    public Integer getStepNumber() { return stepNumber; }

    public void setVersionId(Long v) { this.versionId = v; }
    public void setFileType(String v) { this.fileType = v; }
    public void setMinioBucket(String v) { this.minioBucket = v; }
    public void setMinioKey(String v) { this.minioKey = v; }
    public void setFileName(String v) { this.fileName = v; }
    public void setFileSizeBytes(Long v) { this.fileSizeBytes = v; }
    public void setContentType(String v) { this.contentType = v; }
    public void setSha256Hash(String v) { this.sha256Hash = v; }
    public void setUploadedBy(Long v) { this.uploadedBy = v; }
    public void setRenditionKind(String v) { this.renditionKind = v; }
    public void setStepNumber(Integer v) { this.stepNumber = v; }
}
