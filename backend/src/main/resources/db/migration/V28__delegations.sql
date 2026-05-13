-- V28: M8 위임(Delegation) 테이블 — QA 매니저 사전 승인 필수 (ADR 0011)

CREATE TABLE delegations (
    id                    BIGSERIAL    PRIMARY KEY,
    delegator_user_id     BIGINT       NOT NULL REFERENCES users(id),
    delegate_user_id      BIGINT       NOT NULL REFERENCES users(id),
    scope_kind            VARCHAR(30)  NOT NULL,
    scope_value           VARCHAR(100),
    reason                TEXT         NOT NULL,
    valid_from            TIMESTAMPTZ  NOT NULL,
    valid_to              TIMESTAMPTZ  NOT NULL,
    state                 VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    qa_approver_user_id   BIGINT       REFERENCES users(id),
    qa_approved_at        TIMESTAMPTZ,
    qa_rejection_reason   TEXT,
    revoked_at            TIMESTAMPTZ,
    revoked_by_user_id    BIGINT       REFERENCES users(id),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_dlg_state  CHECK (state IN ('REQUESTED','APPROVED','REJECTED','EXPIRED','REVOKED')),
    CONSTRAINT chk_dlg_scope  CHECK (scope_kind IN ('ALL','APPROVAL_STEP')),
    CONSTRAINT chk_dlg_period CHECK (valid_to > valid_from),
    CONSTRAINT chk_dlg_self   CHECK (delegator_user_id <> delegate_user_id)
);

CREATE INDEX idx_dlg_delegator ON delegations(delegator_user_id, state, valid_from DESC);
CREATE INDEX idx_dlg_active    ON delegations(delegate_user_id, state, valid_from, valid_to)
    WHERE state = 'APPROVED';

COMMENT ON TABLE delegations IS 'M8: 위임 도메인 — 21 CFR Part 11 §11.100(a) 준수, QA 매니저 사전 승인 필수';
COMMENT ON COLUMN delegations.scope_kind IS 'ALL=전 결재 단계 위임 / APPROVAL_STEP=특정 role_code 단계만';
COMMENT ON COLUMN delegations.scope_value IS 'scope_kind=APPROVAL_STEP 시 role_code; 자유 입력 불가 (드롭다운 전용)';
COMMENT ON COLUMN delegations.delegator_user_id IS '위임자 A — markDone() 불가, A의 OPEN work_queue 행은 CANCELLED 처리됨';
COMMENT ON COLUMN delegations.delegate_user_id IS '대리인 B — markDone() 가능, 신규 OPEN work_queue 행 배정';
