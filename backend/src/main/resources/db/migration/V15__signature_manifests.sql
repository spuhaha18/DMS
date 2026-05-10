CREATE TABLE signature_manifests (
    id                BIGSERIAL PRIMARY KEY,
    version_id        BIGINT       NOT NULL REFERENCES document_versions(id),
    workflow_step_id  BIGINT       REFERENCES workflow_step_instances(id),
    signer_id         BIGINT       NOT NULL REFERENCES users(id),
    signer_user_id    VARCHAR(50)  NOT NULL,
    signer_name       VARCHAR(100) NOT NULL,
    meaning           VARCHAR(30)  NOT NULL
                       CHECK (meaning IN ('REVIEWED','APPROVED','QA_APPROVED','RETIRED')),
    signed_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    client_ip         VARCHAR(45),
    canonical_payload TEXT         NOT NULL,
    prev_hash         VARCHAR(64)  NOT NULL,
    this_hash         VARCHAR(64)  NOT NULL,
    session_first     BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_sigmf_version  ON signature_manifests(version_id);
CREATE INDEX idx_sigmf_step     ON signature_manifests(workflow_step_id);
CREATE INDEX idx_sigmf_signer   ON signature_manifests(signer_id);
