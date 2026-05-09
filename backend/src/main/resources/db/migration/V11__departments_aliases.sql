-- V11: departments master + aliases table (DS deviation DEV-DOC-002)
-- HRIS placeholder columns included for future sync integration.
-- M2 backfill: users.department free-text → normalized dept_code
--
-- ENGINEERING FIX (E3): Pre-normalize users.department FIRST to avoid
-- case/whitespace mismatch between INSERT primary_name and UPDATE match condition.

-- Step 1: Pre-normalize all users.department values to dept_code format
-- This ensures INSERT primary_name == users.department for UPDATE matching
UPDATE users
   SET department = UPPER(REGEXP_REPLACE(department, '\s+', '_', 'g'))
 WHERE department IS NOT NULL
   AND department <> '';

-- Step 2: Create departments master table
CREATE TABLE departments (
    id                BIGSERIAL PRIMARY KEY,
    dept_code         VARCHAR(50)  UNIQUE NOT NULL,
    primary_name      VARCHAR(100) NOT NULL,
    external_id       VARCHAR(100),
    source            VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL'
                         CHECK (source IN ('INTERNAL','HRIS')),
    last_synced_at    TIMESTAMPTZ,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        BIGINT REFERENCES users(id),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by        BIGINT REFERENCES users(id)
);
CREATE INDEX idx_departments_code ON departments(dept_code);

-- Step 3: Create aliases table
CREATE TABLE department_aliases (
    id          BIGSERIAL PRIMARY KEY,
    dept_id     BIGINT NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    alias_name  VARCHAR(100) NOT NULL,
    locale      VARCHAR(8),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (dept_id, alias_name)
);

-- Step 4: Insert departments from now-normalized users.department values
-- After step 1, users.department IS already the dept_code, so primary_name = dept_code
INSERT INTO departments (dept_code, primary_name, source, created_at)
SELECT DISTINCT department AS dept_code,
                department AS primary_name,
                'INTERNAL',
                NOW()
FROM users
WHERE department IS NOT NULL
  AND department <> ''
ON CONFLICT (dept_code) DO NOTHING;

-- Step 5: No further UPDATE needed — users.department is already dept_code after step 1

-- Step 6: Add FK from documents.department to departments.dept_code (Phase 4 T1 decision)
-- documents table was created in V9 without this FK (departments didn't exist yet)
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_dept
    FOREIGN KEY (department) REFERENCES departments(dept_code);

-- Step 7: Verification (will fail migration if data integrity violated)
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM users
    WHERE department IS NOT NULL
      AND department <> ''
      AND department NOT IN (SELECT dept_code FROM departments);

    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'V11 backfill failed: % user(s) have department not in departments table', orphan_count;
    END IF;
END $$;
