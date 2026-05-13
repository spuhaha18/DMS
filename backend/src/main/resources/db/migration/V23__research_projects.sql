-- V23: M7.5 연구과제 마스터 + 시험 종류별 보존정책

-- ============================================================
-- 1) Table 1 적용 카테고리 플래그
-- ============================================================
ALTER TABLE document_categories ADD COLUMN uses_table1 BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN document_categories.uses_table1 IS 'M7.5: SOP §6.2.2 Table 1 보존정책 적용 여부 (QA 합의 후 V25에서 TRUE 지정)';

-- ============================================================
-- 2) 시험 종류 마스터 (SOP §6.2.2 Table 1)
-- ============================================================
CREATE TABLE research_project_types (
    type_code        VARCHAR(50)  PRIMARY KEY,
    type_name_kr     VARCHAR(200) NOT NULL,
    type_name_en     VARCHAR(200),
    retention_years  INTEGER,
    is_perpetual     BOOLEAN      NOT NULL DEFAULT FALSE,
    sop_table_row    VARCHAR(50),
    note             TEXT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_rpt_retention CHECK (
        (is_perpetual = TRUE  AND retention_years IS NULL) OR
        (is_perpetual = FALSE AND retention_years IS NOT NULL
                              AND retention_years BETWEEN 1 AND 99)
    )
);
COMMENT ON TABLE research_project_types IS 'M7.5: SOP §6.2.2 Table 1 시험 종류별 보존정책';

-- ============================================================
-- 3) 연구과제 마스터
-- ============================================================
CREATE TABLE research_projects (
    project_code      VARCHAR(50)  PRIMARY KEY,
    project_name      VARCHAR(500) NOT NULL,
    type_code         VARCHAR(50)  NOT NULL REFERENCES research_project_types(type_code),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approval_date     DATE,
    termination_date  DATE,
    pm_user_id        BIGINT REFERENCES users(id),
    department_code   VARCHAR(50)  REFERENCES departments(dept_code),
    description       TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        BIGINT       NOT NULL REFERENCES users(id),
    CONSTRAINT chk_rp_status CHECK (status IN ('ACTIVE','APPROVED','TERMINATED')),
    CONSTRAINT chk_rp_state CHECK (
        (status = 'ACTIVE'      AND approval_date IS NULL     AND termination_date IS NULL) OR
        (status = 'APPROVED'    AND approval_date IS NOT NULL AND termination_date IS NULL) OR
        (status = 'TERMINATED'  AND approval_date IS NULL     AND termination_date IS NOT NULL)
    )
);
CREATE INDEX idx_rp_status ON research_projects(status);
CREATE INDEX idx_rp_type   ON research_projects(type_code);
COMMENT ON TABLE research_projects IS 'M7.5: 연구과제 마스터 (Table 1 적용 GxP 산출물 정박 기준)';

-- ============================================================
-- 4) Envers 감사 테이블 — V5 컨벤션: rev REFERENCES revinfo(rev)
-- ============================================================
CREATE TABLE research_project_types_aud (
    type_code        VARCHAR(50)  NOT NULL,
    rev              INTEGER      NOT NULL REFERENCES revinfo(rev),
    revtype          SMALLINT,
    type_name_kr     VARCHAR(200),
    type_name_en     VARCHAR(200),
    retention_years  INTEGER,
    is_perpetual     BOOLEAN,
    sop_table_row    VARCHAR(50),
    note             TEXT,
    active           BOOLEAN,
    created_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ,
    PRIMARY KEY (type_code, rev)
);

CREATE TABLE research_projects_aud (
    project_code      VARCHAR(50)  NOT NULL,
    rev               INTEGER      NOT NULL REFERENCES revinfo(rev),
    revtype           SMALLINT,
    project_name      VARCHAR(500),
    type_code         VARCHAR(50),
    status            VARCHAR(20),
    approval_date     DATE,
    termination_date  DATE,
    pm_user_id        BIGINT,
    department_code   VARCHAR(50),
    description       TEXT,
    created_at        TIMESTAMPTZ,
    created_by        BIGINT,
    updated_at        TIMESTAMPTZ,
    updated_by        BIGINT,
    PRIMARY KEY (project_code, rev)
);

-- ============================================================
-- 5) 시드 — SOP §6.2.2 Table 1 (4종, TBD 표시 포함)
-- ============================================================
INSERT INTO research_project_types
    (type_code, type_name_kr, type_name_en, retention_years, is_perpetual, sop_table_row, note)
VALUES
    ('CHEM_NEW',     '화학신약',        'Chemical New Drug', 20, FALSE, 'Table1.1', 'SOP §6.2.2 Table 1 행1'),
    ('OTC',          '일반의약품(OTC)', 'OTC',                20, FALSE, 'Table1.2', 'SOP §6.2.2 Table 1 행2'),
    ('BD_TEST',      'B&D 시험제',      'B&D Trial',          15, FALSE, 'Table1.3', 'SOP §6.2.2 Table 1 행3'),
    ('TRIAL_MARKET', '시험제(시판)',    'Trial (Marketed)',   15, FALSE, 'Table1.4', 'TBD: QA에 4번째 행 정확한 N값 확인 필요 (임시 15년 — V25에서 정정)');

-- ============================================================
-- 6) documents.project_code: 자유 텍스트 → research_projects FK
--    기존 값은 project_code_legacy로 백업 후 NULL 전환
-- ============================================================
ALTER TABLE documents ADD COLUMN project_code_legacy VARCHAR(100);
COMMENT ON COLUMN documents.project_code_legacy IS 'M7.5: V23 이전 자유 텍스트 project_code 백업 (마이그레이션 전용)';

UPDATE documents SET project_code_legacy = project_code WHERE project_code IS NOT NULL;
UPDATE documents SET project_code = NULL;

ALTER TABLE documents ALTER COLUMN project_code TYPE VARCHAR(50);
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_project_code
    FOREIGN KEY (project_code) REFERENCES research_projects(project_code);

COMMENT ON COLUMN documents.project_code IS 'M7.5: research_projects FK (이전 자유 텍스트 값은 project_code_legacy에 보존)';

-- ============================================================
-- 7) 채번 CHECK 갱신 — V10 실제 4종 → 5종 (PER_YEAR 보존 필수)
--    PER_PRODUCT → PER_PROJECT 리네임 (alias는 V26에서 제거)
-- ============================================================
ALTER TABLE numbering_templates DROP CONSTRAINT IF EXISTS numbering_templates_counter_scope_check;
ALTER TABLE numbering_templates
    ADD CONSTRAINT numbering_templates_counter_scope_check
    CHECK (counter_scope IN ('PER_DEPT','PER_PRODUCT','PER_PROJECT','PER_YEAR','GLOBAL'));

UPDATE numbering_templates SET counter_scope = 'PER_PROJECT' WHERE counter_scope = 'PER_PRODUCT';
-- PER_PRODUCT alias: NumberingService에서 6개월 유지 후 V26에서 DB CHECK 제거
