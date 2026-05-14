-- V32: notification_outbox 테이블 누락 컬럼 추가 (V26 누락 보완)
-- 엔티티(NotificationOutbox)와 DB 스키마 동기화
ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(1000);

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
