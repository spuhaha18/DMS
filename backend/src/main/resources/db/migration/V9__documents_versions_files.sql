-- V9: Documents, document_versions, document_files tables
-- Full schema including columns for M4 (state machine), M7 (pdf), M8 (search)
-- M3 code only uses the subset relevant to DRAFT creation and file upload.
--
-- NOTE: documents.department FK to departments(dept_code) is added in V11
-- (after departments table is created by V11).

CREATE TABLE documents (
    id              BIGSERIAL PRIMARY KEY,
    doc_number      VARCHAR(50)  NOT NULL UNIQUE,
    category_id     BIGINT       NOT NULL REFERENCES document_categories(id),
    department      VARCHAR(50)  NOT NULL,
    project_code    VARCHAR(100),
    title           VARCHAR(500) NOT NULL,
    owner_id        BIGINT       NOT NULL REFERENCES users(id),
    confidential    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT       NOT NULL REFERENCES users(id)
);
CREATE INDEX idx_documents_category ON documents(category_id);
CREATE INDEX idx_documents_dept     ON documents(department);
CREATE INDEX idx_documents_owner    ON documents(owner_id);

CREATE TABLE document_versions (
    id                BIGSERIAL PRIMARY KEY,
    document_id       BIGINT      NOT NULL REFERENCES documents(id),
    revision          INTEGER,
    state             VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    title             VARCHAR(500),
    change_summary    TEXT,
    reason_for_change TEXT,
    source_file_key   VARCHAR(500),
    pdf_file_key      VARCHAR(500),
    pdf_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    effective_date    DATE,
    expiry_date       DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        BIGINT      NOT NULL REFERENCES users(id),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by        BIGINT      NOT NULL REFERENCES users(id)
);
CREATE INDEX idx_docversions_docid ON document_versions(document_id);
CREATE INDEX idx_docversions_state ON document_versions(state);

CREATE TABLE document_files (
    id              BIGSERIAL PRIMARY KEY,
    version_id      BIGINT       NOT NULL REFERENCES document_versions(id),
    file_type       VARCHAR(20)  NOT NULL CHECK (file_type IN ('ORIGINAL','RENDITION')),
    minio_bucket    VARCHAR(100) NOT NULL,
    minio_key       VARCHAR(500) NOT NULL,
    file_name       VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    content_type    VARCHAR(100),
    sha256_hash     VARCHAR(64)  NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    uploaded_by     BIGINT       NOT NULL REFERENCES users(id)
);
CREATE INDEX idx_docfiles_version ON document_files(version_id);

-- M8 search columns (added here as NULL placeholders to avoid ALTER later)
ALTER TABLE documents         ADD COLUMN search_vector TSVECTOR;
ALTER TABLE document_versions ADD COLUMN search_vector TSVECTOR;
