CREATE TABLE workflow_instances (
    id              BIGSERIAL PRIMARY KEY,
    version_id      BIGINT       NOT NULL REFERENCES document_versions(id),
    template_id     BIGINT       NOT NULL REFERENCES workflow_templates(id),
    state           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS'
                       CHECK (state IN ('IN_PROGRESS','COMPLETED','REJECTED','CANCELLED')),
    current_step    INTEGER      NOT NULL DEFAULT 1,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_by      VARCHAR(50)  NOT NULL,
    completed_at    TIMESTAMPTZ,
    completed_by    VARCHAR(50)
);
CREATE INDEX idx_wfi_version ON workflow_instances(version_id);
CREATE UNIQUE INDEX uq_one_active_workflow_per_version
    ON workflow_instances(version_id) WHERE state = 'IN_PROGRESS';

CREATE TABLE workflow_step_instances (
    id               BIGSERIAL PRIMARY KEY,
    workflow_id      BIGINT       NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_order       INTEGER      NOT NULL,
    step_type        VARCHAR(20)  NOT NULL,
    role_code        VARCHAR(50)  NOT NULL,
    min_signers      INTEGER      NOT NULL,
    parallel         BOOLEAN      NOT NULL,
    qa_required      BOOLEAN      NOT NULL,
    state            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (state IN ('PENDING','IN_PROGRESS','COMPLETED','REJECTED')),
    assignees        JSONB        NOT NULL DEFAULT '[]',
    signed           JSONB        NOT NULL DEFAULT '[]',
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    rejection_reason TEXT,
    UNIQUE (workflow_id, step_order)
);
