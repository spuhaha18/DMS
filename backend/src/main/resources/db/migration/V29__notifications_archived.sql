-- V29: M8 알림 아카이브 테이블 — notifications 90일 read 후 이전, 5년 보존 (ADR 0005)

CREATE TABLE notifications_archived (
    id                      BIGINT       PRIMARY KEY,
    recipient_id            BIGINT       NOT NULL,
    event_code              VARCHAR(60)  NOT NULL,
    category                VARCHAR(40)  NOT NULL,
    severity                VARCHAR(20)  NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    body                    TEXT,
    related_document_id     BIGINT,
    related_version_id      BIGINT,
    related_work_item_id    BIGINT,
    link_path               VARCHAR(500),
    is_read                 BOOLEAN      NOT NULL DEFAULT TRUE,
    read_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    archived_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_na_recipient ON notifications_archived(recipient_id, created_at DESC);
CREATE INDEX idx_na_vacuum    ON notifications_archived(archived_at);

COMMENT ON TABLE notifications_archived IS 'M8: 90일 경과 read 알림 보관소 — ALCOA+ Enduring 5년 후 vacuum (ADR 0005)';
COMMENT ON COLUMN notifications_archived.id IS 'notifications.id 원본 값 그대로 보존 — FK는 걸지 않음 (원본 삭제 후 아카이브에만 존재)';
COMMENT ON COLUMN notifications_archived.archived_at IS 'NotificationArchiveJob 실행 시각 — vacuum 기준점';
