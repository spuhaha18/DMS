package com.lab.edms.signature;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class SignatureRequest {

    private Long stepInstanceId;

    @NotBlank
    private String password;

    @NotBlank
    private String meaning;

    @JsonProperty("signing_user_id")
    private String signingUserId;  // optional; required only for session-first signing

    public Long getStepInstanceId() { return stepInstanceId; }
    public void setStepInstanceId(Long stepInstanceId) { this.stepInstanceId = stepInstanceId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getMeaning() { return meaning; }
    public void setMeaning(String meaning) { this.meaning = meaning; }

    public String getSigningUserId() { return signingUserId; }
    public void setSigningUserId(String signingUserId) { this.signingUserId = signingUserId; }
}
