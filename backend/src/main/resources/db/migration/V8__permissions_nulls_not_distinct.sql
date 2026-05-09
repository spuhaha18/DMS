-- DS Deviation: PG16 NULLS NOT DISTINCT to prevent duplicate org-wide rows
-- (department NULL means "all departments"; only one such row per (role,category) is valid).

ALTER TABLE permissions DROP CONSTRAINT permissions_role_id_category_id_department_key;
ALTER TABLE permissions
    ADD CONSTRAINT permissions_role_category_dept_uniq
    UNIQUE NULLS NOT DISTINCT (role_id, category_id, department);
