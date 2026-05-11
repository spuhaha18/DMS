package com.lab.edms.signature;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public class SignatureSummaryDto {

    private Long id;

    @JsonProperty("signer_name")
    private String signerName;

    private String meaning;

    @JsonProperty("signed_at")
    private OffsetDateTime signedAt;

    @JsonProperty("session_first")
    private boolean sessionFirst;

    @JsonProperty("algorithm_version")
    private String algorithmVersion;

    public SignatureSummaryDto(SignatureManifest m) {
        this.id = m.getId();
        this.signerName = m.getSignerName();
        this.meaning = m.getMeaning() != null ? m.getMeaning().name() : null;
        this.signedAt = m.getSignedAt();
        this.sessionFirst = m.isSessionFirst();
        this.algorithmVersion = m.getAlgorithmVersion();
    }

    public Long getId() { return id; }
    public String getSignerName() { return signerName; }
    public String getMeaning() { return meaning; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public boolean isSessionFirst() { return sessionFirst; }
    public String getAlgorithmVersion() { return algorithmVersion; }
}
