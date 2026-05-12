-- V21: PDF 파이프라인 컬럼 + sign_intents 테이블
-- M7 PR1: pdf_status 상태기계, document_files 단계별 누적, sign_intents (M7 PR3 사용)

-- 1. documents 테이블 — pdf_status 상태기계
ALTER TABLE documents
  ADD COLUMN pdf_status VARCHAR(32) NULL,
  ADD COLUMN pdf_status_updated_at TIMESTAMPTZ NULL,
  ADD COLUMN pdf_status_reason VARCHAR(64) NULL;

COMMENT ON COLUMN documents.pdf_status IS
  'PDF 파이프라인 상태기계. NULL=미시작, PENDING_CONVERSION/CONVERTED/STAMPING/STAMPED/WATERMARKING/EFFECTIVE_STAMPED, 분기 CONVERSION_FAILED/STAMP_FAILED';

-- 2. document_files 테이블 — 단계별 RENDITION 누적
ALTER TABLE document_files
  ADD COLUMN step_number INTEGER NULL,
  ADD COLUMN rendition_kind VARCHAR(16) NULL;

-- invariant: rendition_kind='STAMPED' 만 step_number NOT NULL
ALTER TABLE document_files
  ADD CONSTRAINT ck_docfile_stamped_step
  CHECK (
    (rendition_kind = 'STAMPED' AND step_number IS NOT NULL)
    OR (rendition_kind IN ('INITIAL','EFFECTIVE') AND step_number IS NULL)
    OR rendition_kind IS NULL
  );

ALTER TABLE document_files
  ADD CONSTRAINT ck_docfile_rendition_kind
  CHECK (rendition_kind IS NULL OR rendition_kind IN ('INITIAL','STAMPED','EFFECTIVE'));

-- 3. sign_intents 테이블 — sign() 트랜잭션이 INSERT, 워커가 manifest INSERT 후 STAMPED 전이
CREATE TABLE sign_intents (
  id                  BIGSERIAL PRIMARY KEY,
  version_id          BIGINT        NOT NULL REFERENCES document_versions(id),
  step_instance_id    BIGINT        NOT NULL REFERENCES workflow_step_instances(id),
  step_number         INTEGER       NOT NULL,
  signer_user_id      VARCHAR(64)   NOT NULL,
  signer_db_id        BIGINT        NOT NULL,
  signed_at           TIMESTAMPTZ   NOT NULL,
  meaning             VARCHAR(64)   NOT NULL,
  signature_payload   JSONB         NOT NULL,
  pubkey_fingerprint  VARCHAR(128)  NULL,
  status              VARCHAR(32)   NOT NULL DEFAULT 'PENDING_STAMP',
  manifest_id         BIGINT        NULL,
  retry_count         INTEGER       NOT NULL DEFAULT 0,
  last_error          TEXT          NULL,
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_sign_intents_status
    CHECK (status IN ('PENDING_STAMP','STAMPED','FAILED'))
);

CREATE INDEX idx_sign_intents_version ON sign_intents(version_id);
CREATE INDEX idx_sign_intents_status  ON sign_intents(status) WHERE status = 'PENDING_STAMP';
