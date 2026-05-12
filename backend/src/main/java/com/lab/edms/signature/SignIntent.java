package com.lab.edms.signature;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lab.edms.pdf.StampPayload;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * M7 PR3: sign() 트랜잭션이 INSERT하는 의도 레코드.
 * 워커(PdfRenditionPipeline)가 stamp 성공 후 manifest INSERT + status=STAMPED 전이.
 */
@Entity
@Table(name = "sign_intents")
public class SignIntent {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "step_instance_id", nullable = false)
    private Long stepInstanceId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "signer_user_id", nullable = false, length = 64)
    private String signerUserId;

    @Column(name = "signer_db_id", nullable = false)
    private Long signerDbId;

    @Column(name = "signed_at", nullable = false)
    private OffsetDateTime signedAt;

    @Column(name = "meaning", nullable = false, length = 64)
    private String meaning;

    /**
     * JSONB: 서명 관련 페이로드 (서명 base64, displayName 등 포함)
     * 패턴: @JdbcTypeCode(SqlTypes.JSON) + @Column(columnDefinition = "jsonb")
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signature_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> signaturePayload;

    @Column(name = "pubkey_fingerprint", length = 128)
    private String pubkeyFingerprint;

    /** 상태: PENDING_STAMP | STAMPED | FAILED */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING_STAMP";

    @Column(name = "manifest_id")
    private Long manifestId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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

    // -----------------------------------------------------------------------
    // Helper: StampPayload 변환
    // -----------------------------------------------------------------------

    /**
     * sign_intents 행을 StampPayload로 변환.
     * Worker가 호출 — DB 재조회 없이 이 레코드에서 모든 값을 추출한다.
     */
    public StampPayload toStampPayload() {
        return new StampPayload(
                id,
                versionId,
                stepNumber,
                signerUserId,
                getDisplayName(),
                signedAt.toInstant(),
                meaning,
                getSignatureBase64(),
                pubkeyFingerprint
        );
    }

    /** signaturePayload JSONB에서 displayName 추출. */
    private String getDisplayName() {
        if (signaturePayload == null) return signerUserId;
        Object v = signaturePayload.get("displayName");
        return v != null ? v.toString() : signerUserId;
    }

    /** signaturePayload JSONB에서 signatureBase64 추출. */
    private String getSignatureBase64() {
        if (signaturePayload == null) return null;
        Object v = signaturePayload.get("signatureBase64");
        return v != null ? v.toString() : null;
    }

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public Long getStepInstanceId() { return stepInstanceId; }
    public Integer getStepNumber() { return stepNumber; }
    public String getSignerUserId() { return signerUserId; }
    public Long getSignerDbId() { return signerDbId; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public String getMeaning() { return meaning; }
    public Map<String, Object> getSignaturePayload() { return signaturePayload; }
    public String getPubkeyFingerprint() { return pubkeyFingerprint; }
    public String getStatus() { return status; }
    public Long getManifestId() { return manifestId; }
    public Integer getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setVersionId(Long v) { this.versionId = v; }
    public void setStepInstanceId(Long v) { this.stepInstanceId = v; }
    public void setStepNumber(Integer v) { this.stepNumber = v; }
    public void setSignerUserId(String v) { this.signerUserId = v; }
    public void setSignerDbId(Long v) { this.signerDbId = v; }
    public void setSignedAt(OffsetDateTime v) { this.signedAt = v; }
    public void setMeaning(String v) { this.meaning = v; }
    public void setSignaturePayload(Map<String, Object> v) { this.signaturePayload = v; }
    public void setPubkeyFingerprint(String v) { this.pubkeyFingerprint = v; }
    public void setStatus(String v) { this.status = v; }
    public void setManifestId(Long v) { this.manifestId = v; }
    public void setRetryCount(Integer v) { this.retryCount = v; }
    public void setLastError(String v) { this.lastError = v; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
