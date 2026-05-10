package com.lab.edms.signature;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "signature_manifests")
public class SignatureManifest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "workflow_step_id")
    private Long workflowStepId;  // nullable (retire 시 null)

    @Column(name = "signer_id", nullable = false)
    private Long signerId;

    @Column(name = "signer_user_id", nullable = false, length = 50)
    private String signerUserId;

    @Column(name = "signer_name", nullable = false, length = 100)
    private String signerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "meaning", nullable = false, length = 30)
    private SignatureMeaning meaning;

    @Column(name = "signed_at", nullable = false)
    private OffsetDateTime signedAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "canonical_payload", nullable = false)
    private String canonicalPayload;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "this_hash", nullable = false, length = 64)
    private String thisHash;

    @Column(name = "session_first", nullable = false)
    private boolean sessionFirst = false;

    @PrePersist
    protected void onCreate() {
        if (signedAt == null) signedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public Long getWorkflowStepId() { return workflowStepId; }
    public Long getSignerId() { return signerId; }
    public String getSignerUserId() { return signerUserId; }
    public String getSignerName() { return signerName; }
    public SignatureMeaning getMeaning() { return meaning; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public String getClientIp() { return clientIp; }
    public String getCanonicalPayload() { return canonicalPayload; }
    public String getPrevHash() { return prevHash; }
    public String getThisHash() { return thisHash; }
    public boolean isSessionFirst() { return sessionFirst; }

    public void setVersionId(Long v) { this.versionId = v; }
    public void setWorkflowStepId(Long v) { this.workflowStepId = v; }
    public void setSignerId(Long v) { this.signerId = v; }
    public void setSignerUserId(String v) { this.signerUserId = v; }
    public void setSignerName(String v) { this.signerName = v; }
    public void setMeaning(SignatureMeaning v) { this.meaning = v; }
    public void setSignedAt(OffsetDateTime v) { this.signedAt = v; }
    public void setClientIp(String v) { this.clientIp = v; }
    public void setCanonicalPayload(String v) { this.canonicalPayload = v; }
    public void setPrevHash(String v) { this.prevHash = v; }
    public void setThisHash(String v) { this.thisHash = v; }
    public void setSessionFirst(boolean v) { this.sessionFirst = v; }
}
