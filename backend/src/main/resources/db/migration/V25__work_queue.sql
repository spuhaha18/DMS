-- V25: M8 통합 work queue (결재·교육·정기검토·Read&Ack)

CREATE TABLE work_queue (
    id                      BIGSERIAL    PRIMARY KEY,
    kind                    VARCHAR(40)  NOT NULL,
    state                   VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    assignee_user_id        BIGINT       NOT NULL REFERENCES users(id),
    delegated_from_user_id  BIGINT       REFERENCES users(id),
    source_type             VARCHAR(40)  NOT NULL,
    source_id               BIGINT       NOT NULL,
    related_document_id     BIGINT       REFERENCES documents(id),
    related_version_id      BIGINT       REFERENCES document_versions(id),
    title                   VARCHAR(200) NOT NULL,
    summary                 TEXT,
    link_path               VARCHAR(500) NOT NULL,
    priority                VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    due_at                  TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    completed_by_user_id    BIGINT       REFERENCES users(id),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_wq_kind CHECK (kind IN ('APPROVAL','TRAINING','PERIODIC_REVIEW','READACK')),
    CONSTRAINT chk_wq_state CHECK (state IN ('OPEN','DONE','CANCELLED','EXPIRED')),
    CONSTRAINT chk_wq_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT chk_wq_completion CHECK (
        (state = 'OPEN' AND completed_at IS NULL) OR
        (state IN ('DONE','CANCELLED','EXPIRED') AND completed_at IS NOT NULL)
    ),
    CONSTRAINT uq_wq_source UNIQUE (kind, source_type, source_id, assignee_user_id)
);

CREATE INDEX idx_wq_assignee_state ON work_queue(assignee_user_id, state, created_at DESC)
    WHERE state = 'OPEN';
CREATE INDEX idx_wq_assignee_all ON work_queue(assignee_user_id, created_at DESC);
CREATE INDEX idx_wq_source ON work_queue(source_type, source_id);

COMMENT ON TABLE work_queue IS 'M8: 통합 work queue (결재·교육·정기검토·Read&Ack)';
COMMENT ON COLUMN work_queue.source_type IS '예: WORKFLOW_STEP_INSTANCE / TRAINING_ASSIGNMENT / PERIODIC_REVIEW / READACK_ASSIGNMENT';
COMMENT ON COLUMN work_queue.delegated_from_user_id IS '위임자 — null이면 직접 배정';
COMMENT ON CONSTRAINT uq_wq_source ON work_queue IS 'WorkQueueReconciler 중복 방지 — 동일 source에 동일 assignee OPEN 행 1건만 허용';
