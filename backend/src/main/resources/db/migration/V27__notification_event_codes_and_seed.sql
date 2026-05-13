-- V27: M8 notification_event_codes 참조 테이블 + 초기 시드 4건

CREATE TABLE notification_event_codes (
    code              VARCHAR(60)   PRIMARY KEY,
    category          VARCHAR(40)   NOT NULL,
    default_severity  VARCHAR(20)   NOT NULL DEFAULT 'INFO',
    default_channels  TEXT[]        NOT NULL DEFAULT ARRAY['IN_APP']::TEXT[],
    template_key      VARCHAR(80)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_evt_severity CHECK (default_severity IN ('INFO','WARN','URGENT'))
);

COMMENT ON TABLE notification_event_codes IS 'M8: 알림 이벤트 코드 참조 테이블 — Postgres CHECK enum 대신 확장성 확보 (ADR 0009)';
COMMENT ON COLUMN notification_event_codes.default_channels IS '기본 채널 목록 (IN_APP / EMAIL_LOG / EMAIL_SMTP)';

INSERT INTO notification_event_codes (code, category, default_severity, default_channels, template_key) VALUES
    ('WORKFLOW_SUBMITTED',  'WORKFLOW',  'INFO', ARRAY['IN_APP','EMAIL_SMTP'], 'workflow.submitted'),
    ('WORKFLOW_SIGNED',     'WORKFLOW',  'INFO', ARRAY['IN_APP'],              'workflow.signed'),
    ('WORKFLOW_REJECTED',   'WORKFLOW',  'WARN', ARRAY['IN_APP','EMAIL_SMTP'], 'workflow.rejected'),
    ('DOCUMENT_EFFECTIVE',  'DOCUMENT',  'INFO', ARRAY['IN_APP','EMAIL_SMTP'], 'document.effective');
