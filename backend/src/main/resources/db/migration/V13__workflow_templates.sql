CREATE TABLE workflow_templates (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES document_categories(id) UNIQUE,
    template_name   VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(50) NOT NULL
);

CREATE TABLE workflow_template_steps (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT      NOT NULL REFERENCES workflow_templates(id) ON DELETE CASCADE,
    step_order      INTEGER     NOT NULL,
    step_type       VARCHAR(20) NOT NULL CHECK (step_type IN ('REVIEW','APPROVAL')),
    role_code       VARCHAR(50) NOT NULL,
    min_signers     INTEGER     NOT NULL DEFAULT 1 CHECK (min_signers > 0),
    parallel        BOOLEAN     NOT NULL DEFAULT FALSE,
    auto_assign     BOOLEAN     NOT NULL DEFAULT FALSE,
    qa_required     BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (template_id, step_order)
);
CREATE INDEX idx_wfts_template ON workflow_template_steps(template_id);
