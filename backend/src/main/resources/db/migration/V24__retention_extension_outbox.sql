-- V24: M7.5 보존기간 연장 outbox (비동기 MinIO extendRetention 큐)

CREATE TABLE retention_extension_outbox (
    id                BIGSERIAL    PRIMARY KEY,
    project_code      VARCHAR(50)  NOT NULL REFERENCES research_projects(project_code),
    document_file_id  BIGINT       NOT NULL REFERENCES document_files(id),
    bucket            VARCHAR(100) NOT NULL,
    object_key        VARCHAR(500) NOT NULL,
    new_retain_until  TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts          INTEGER      NOT NULL DEFAULT 0,
    last_error        TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_at         TIMESTAMPTZ,
    locked_by         VARCHAR(100),
    processed_at      TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSING','SUCCESS','FAILED'))
);

-- 워커 클레임 쿼리 최적화 (FOR UPDATE SKIP LOCKED 대상)
CREATE INDEX idx_outbox_pending    ON retention_extension_outbox(status, attempts)
    WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX idx_outbox_by_project ON retention_extension_outbox(project_code, status);

COMMENT ON TABLE retention_extension_outbox IS
    'M7.5: MinIO extendRetention 비동기 큐 — FOR UPDATE SKIP LOCKED + 5회 재시도 후 dead-letter';
COMMENT ON COLUMN retention_extension_outbox.locked_by IS
    'RetentionExtensionWorker 인스턴스 ID (worker-UUID) — 동시성 추적용';
