-- DS Deviation §6.7.1: v_access_review for SOP-USER-001 quarterly access review.

CREATE OR REPLACE VIEW v_access_review AS
SELECT
    u.id                AS user_pk,
    u.user_id           AS user_id,
    u.full_name         AS full_name,
    u.email             AS email,
    u.department        AS department,
    u.title             AS title,
    u.status            AS status,
    u.valid_from        AS valid_from,
    u.valid_until       AS valid_until,
    u.last_login_at     AS last_login_at,
    u.created_at        AS created_at,
    string_agg(r.role_code, ',' ORDER BY r.role_code) AS role_codes
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r ON r.id = ur.role_id
GROUP BY u.id;

GRANT SELECT ON v_access_review TO app_role;
