-- V31__training_assignments.sql
CREATE TABLE training_assignments (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id),
    version_id        BIGINT       NOT NULL REFERENCES document_versions(id),
    assigned_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    assigned_by       VARCHAR(50)  NOT NULL,
    due_at            TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    completion_sig_id BIGINT       REFERENCES signature_manifests(id),
    UNIQUE (user_id, version_id)
);

CREATE INDEX idx_training_user    ON training_assignments(user_id);
CREATE INDEX idx_training_version ON training_assignments(version_id);
CREATE INDEX idx_training_done    ON training_assignments(completed_at) WHERE completed_at IS NULL;
