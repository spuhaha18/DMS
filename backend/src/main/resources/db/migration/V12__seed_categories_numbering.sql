-- V12: Seed 4 document categories and their numbering templates
-- Categories: SOP, METHOD, SPEC, FORM
-- Templates: PER_DEPT for SOP/METHOD/FORM, PER_PRODUCT for SPEC

INSERT INTO document_categories (category_code, category_name, review_period_months, qa_mandatory, is_active)
VALUES
    ('SOP',    '표준작업절차서', 24, TRUE,  TRUE),
    ('METHOD', '시험방법',       24, TRUE,  TRUE),
    ('SPEC',   '제품규격',       12, TRUE,  TRUE),
    ('FORM',   '서식',           36, FALSE, TRUE)
ON CONFLICT (category_code) DO NOTHING;

-- Seed numbering templates (linked to admin user from V4 bootstrap)
INSERT INTO numbering_templates (category_id, format_pattern, counter_scope, created_by, updated_by)
SELECT c.id, t.pattern, t.scope, u.id, u.id
FROM (VALUES
    ('SOP',    '{TYPE}-{DEPT}-{SEQ:3}', 'PER_DEPT'),
    ('METHOD', '{TYPE}-{DEPT}-{SEQ:3}', 'PER_DEPT'),
    ('SPEC',   '{TYPE}-{PROD}-{SEQ:3}', 'PER_PRODUCT'),
    ('FORM',   '{DEPT}-F-{SEQ:3}',      'PER_DEPT')
) AS t(code, pattern, scope)
JOIN document_categories c ON c.category_code = t.code
JOIN users u ON u.user_id = 'admin'
ON CONFLICT (category_id) DO NOTHING;
