-- V10: Numbering templates and counters for document number generation
-- NumberingService uses SELECT FOR UPDATE on numbering_counters to ensure
-- gap-less, duplicate-free sequences under concurrent load.

CREATE TABLE numbering_templates (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES document_categories(id) UNIQUE,
    format_pattern  VARCHAR(200) NOT NULL,
    counter_scope   VARCHAR(20) NOT NULL CHECK (counter_scope IN
                       ('PER_DEPT','PER_PRODUCT','PER_YEAR','GLOBAL')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT      NOT NULL REFERENCES users(id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      BIGINT      NOT NULL REFERENCES users(id)
);

CREATE TABLE numbering_counters (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES numbering_templates(category_id),
    scope_key       VARCHAR(100) NOT NULL,
    current_seq     INTEGER     NOT NULL DEFAULT 0,
    UNIQUE (category_id, scope_key)
);
