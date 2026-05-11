-- V18: audit_checkpoints + INSERT-only hardening
-- DS §4.2 lines 514-521 + §4.3 lines 605-613, FS-AUD-003, OAS AuditCheckpoint

-- =============================================================
-- audit_checkpoints — 일별 WORM 앵커 인덱스
-- =============================================================
CREATE TABLE audit_checkpoints (
    id                BIGSERIAL PRIMARY KEY,
    checkpoint_date   DATE         NOT NULL UNIQUE,   -- KST 캘린더일
    merkle_root       VARCHAR(64)  NOT NULL,
    record_count      BIGINT       NOT NULL,
    first_log_id      BIGINT,                          -- 0건이면 NULL
    last_log_id       BIGINT,                          -- 0건이면 NULL
    prev_anchor_hash  VARCHAR(64)  NOT NULL,           -- 앵커 간 체인
    anchor_hash       VARCHAR(64)  NOT NULL UNIQUE,    -- SHA-256(prev|merkle|date|count|first|last)
    minio_key         VARCHAR(500) NOT NULL,
    generated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_checkpoints_date ON audit_checkpoints(checkpoint_date);

-- =============================================================
-- audit_role 존재성 가드 (V2 패턴 재사용)
-- =============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_role') THEN
        RAISE EXCEPTION 'audit_role principal does not exist; check infra/postgres/init/00-create-audit-role.sh';
    END IF;
END
$$;

-- =============================================================
-- 권한 하드닝 (DS §4.3)
-- =============================================================
-- audit_checkpoints 신규
REVOKE UPDATE, DELETE, TRUNCATE ON audit_checkpoints  FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_checkpoints  FROM app_role;
GRANT  INSERT, SELECT ON audit_checkpoints  TO audit_role;
GRANT  USAGE,  SELECT ON SEQUENCE audit_checkpoints_id_seq TO audit_role;

-- signature_manifests V15 누락 보강 — app_role 의 UPDATE/DELETE 박탈
REVOKE UPDATE, DELETE, TRUNCATE ON signature_manifests FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON signature_manifests FROM app_role;
GRANT  INSERT, SELECT ON signature_manifests TO audit_role;
GRANT  USAGE,  SELECT ON SEQUENCE signature_manifests_id_seq TO audit_role;
