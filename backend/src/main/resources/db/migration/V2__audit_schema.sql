-- V2: audit_logs (INSERT-only via dedicated audit_role principal)
-- DS §4.2 lines 495-512, §4.3, §8.2

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT,                                       -- NULL = system
    actor_user_id   VARCHAR(50),                                  -- denormalized
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       VARCHAR(100),
    before_value    JSONB,
    after_value     JSONB,
    reason          TEXT,
    client_ip       VARCHAR(45),
    server_ts       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    prev_hash       VARCHAR(64) NOT NULL,
    this_hash       VARCHAR(64) NOT NULL UNIQUE
);

CREATE INDEX idx_audit_logs_server_ts ON audit_logs(server_ts);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_user_id, server_ts);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

-- Tamper-evidence at the database principal level.
-- audit_role is created in postgres init script (infra/postgres/init).
-- Verify role exists; fail migration if not.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_role') THEN
        RAISE EXCEPTION 'audit_role principal does not exist; check infra/postgres/init/00-create-audit-role.sh';
    END IF;
END
$$;

-- Grants: audit_role gets INSERT and SELECT (for prev_hash chain reads) only.
-- No UPDATE, no DELETE, no TRUNCATE — these are not granted, so attempting them yields permission denied.
GRANT USAGE ON SCHEMA public TO audit_role;
GRANT INSERT, SELECT ON audit_logs TO audit_role;
GRANT USAGE, SELECT ON SEQUENCE audit_logs_id_seq TO audit_role;

-- Defensive: revoke mutation rights from PUBLIC and from app_role.
-- app_role uses Flyway DDL at startup but must never mutate GxP records at runtime.
-- The superuser (postgres) can still clean up in dev; prod uses pg_dump restore only.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM app_role;
