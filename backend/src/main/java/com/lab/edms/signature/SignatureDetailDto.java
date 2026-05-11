package com.lab.edms.signature;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignatureDetailDto extends SignatureSummaryDto {

    @JsonProperty("signer_user_id")
    private String signerUserId;

    @JsonProperty("client_ip")
    private String clientIp;

    @JsonProperty("canonical_payload")
    private String canonicalPayload;

    @JsonProperty("prev_hash")
    private String prevHash;

    @JsonProperty("this_hash")
    private String thisHash;

    public SignatureDetailDto(SignatureManifest m) {
        super(m);
        this.signerUserId = m.getSignerUserId();
        this.clientIp = m.getClientIp();
        this.canonicalPayload = m.getCanonicalPayload();
        this.prevHash = m.getPrevHash();
        this.thisHash = m.getThisHash();
    }

    public String getSignerUserId() { return signerUserId; }
    public String getClientIp() { return clientIp; }
    public String getCanonicalPayload() { return canonicalPayload; }
    public String getPrevHash() { return prevHash; }
    public String getThisHash() { return thisHash; }
}
