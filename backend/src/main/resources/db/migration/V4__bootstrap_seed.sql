-- V4: System role seed + initial admin account
-- 8 predefined roles per FS-USER-003.
-- Initial admin account is created with a placeholder password_hash;
-- the operator MUST run /api/v1/auth/change-password on first login (force_change_pw=true).

INSERT INTO roles (role_code, role_name, description, is_system) VALUES
    ('AUTHOR',   '작성자',   '문서를 작성하고 검토를 요청한다',     TRUE),
    ('REVIEWER', '검토자',   '제출된 문서를 검토하고 의견을 작성한다', TRUE),
    ('APPROVER', '승인자',   '검토 완료된 문서를 최종 승인한다',     TRUE),
    ('QA',       'QA',       '품질 보증 검토 및 정기검토 응답',      TRUE),
    ('RA',       'RA',       '규제 업무 검토',                       TRUE),
    ('READER',   '열람자',   '권한 있는 문서 열람',                  TRUE),
    ('ADMIN',    '관리자',   '시스템 관리 및 사용자/권한 관리',      TRUE),
    ('AUDITOR',  '감사인',   '열람·감사로그 조회 전용 (유효기간)',   TRUE);

-- Bootstrap admin account.
-- BCrypt hash for 'BootstrapMe!2026' generated with rounds=12; force_change_pw=TRUE forces immediate change.
-- Hash regenerated per environment via SOP-USER-001 §5.1; this seed is dev/IQ only.
INSERT INTO users (
    user_id, full_name, email, department, title, status,
    password_hash, force_change_pw, created_at, updated_at
) VALUES (
    'admin', '시스템 관리자', 'admin@lab.internal', 'IT', '시스템 관리자', 'ACTIVE',
    '$2a$12$M.HkGlKf8uG7NQsd8h2X5e0wKpVqwYxVBbZUz/JVlQAzEK1E8yQbS',
    TRUE, NOW(), NOW()
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.user_id = 'admin' AND r.role_code = 'ADMIN';
