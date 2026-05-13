-- V26: M8 알림 핵심 테이블 — notifications + notification_outbox + notification_dlq

CREATE TABLE notifications (
    id                      BIGSERIAL    PRIMARY KEY,
    recipient_id            BIGINT       NOT NULL REFERENCES users(id),
    event_code              VARCHAR(60)  NOT NULL,
    category                VARCHAR(40)  NOT NULL,
    severity                VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    title                   VARCHAR(200) NOT NULL,
    body                    TEXT,
    related_document_id     BIGINT       REFERENCES documents(id),
    related_version_id      BIGINT       REFERENCES document_versions(id),
    related_work_item_id    BIGINT       REFERENCES work_queue(id),
    link_path               VARCHAR(500),
    is_read                 BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_n_severity CHECK (severity IN ('INFO','WARN','URGENT')),
    CONSTRAINT chk_n_read CHECK (
        (is_read = FALSE AND read_at IS NULL) OR
        (is_read = TRUE  AND read_at IS NOT NULL)
    )
);

CREATE INDEX idx_n_recipient_unread ON notifications(recipient_id, is_read, created_at DESC)
    WHERE is_read = FALSE;
CREATE INDEX idx_n_recipient_all    ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_n_archive_lookup   ON notifications(created_at)
    WHERE is_read = TRUE;

COMMENT ON TABLE notifications IS 'M8: in-app 알림 수신 기록 — 90일 read 후 notifications_archived로 이전';
COMMENT ON COLUMN notifications.event_code IS 'notification_event_codes.code FK (논리 참조 — 앱 레이어 검증)';

-- ─── Outbox (OutboxDispatcher 처리 큐) ───────────────────────────────────────

CREATE TABLE notification_outbox (
    id              BIGSERIAL    PRIMARY KEY,
    notification_id BIGINT       REFERENCES notifications(id),
    recipient_id    BIGINT       NOT NULL REFERENCES users(id),
    channel         VARCHAR(30)  NOT NULL,
    event_code      VARCHAR(60)  NOT NULL,
    payload_json    JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_o_channel CHECK (channel IN ('IN_APP','EMAIL_LOG','EMAIL_SMTP')),
    CONSTRAINT chk_o_status  CHECK (status  IN ('PENDING','SENDING','DELIVERED','FAILED','DEAD')),
    CONSTRAINT chk_o_payload_size CHECK (octet_length(payload_json::TEXT) < 65536)
);

CREATE INDEX idx_o_pending   ON notification_outbox(status, next_attempt_at)
    WHERE status IN ('PENDING','FAILED');
CREATE INDEX idx_o_recipient ON notification_outbox(recipient_id, created_at DESC);

COMMENT ON TABLE notification_outbox IS 'M8: OutboxDispatcher 전송 큐 — PENDING→SENDING→DELIVERED|FAILED→DEAD(→DLQ)';
COMMENT ON COLUMN notification_outbox.payload_json IS 'template_key + params만 저장; 65 KB 상한';

-- ─── DLQ (Dead Letter Queue — 5년 보존, ALCOA+) ─────────────────────────────

CREATE TABLE notification_dlq (
    id                      BIGSERIAL    PRIMARY KEY,
    outbox_id               BIGINT       NOT NULL,
    recipient_id            BIGINT       NOT NULL,
    channel                 VARCHAR(30)  NOT NULL,
    event_code              VARCHAR(60)  NOT NULL,
    payload_json            JSONB        NOT NULL,
    error_history           JSONB        NOT NULL,
    moved_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_by_user_id BIGINT       REFERENCES users(id),
    acknowledged_at         TIMESTAMPTZ
);

CREATE INDEX idx_dlq_unack ON notification_dlq(moved_at DESC)
    WHERE acknowledged_at IS NULL;

COMMENT ON TABLE notification_dlq IS 'M8: 3회 재시도 실패 알림 DLQ — GxP 증거로 5년 보존 후 vacuum';
COMMENT ON COLUMN notification_dlq.error_history IS '재시도별 에러 스냅샷 JSONB 배열';
