-- M2 Department Pre-Flight Validation
-- Run this BEFORE V11 migration to detect ambiguous department data.
-- Expected: all queries return 0 rows. Any rows indicate data to fix manually.

-- Check 1: Multiple original spellings that map to the same dept_code
SELECT
    UPPER(REGEXP_REPLACE(department, '\s+', '_', 'g')) AS normalized_code,
    COUNT(DISTINCT department) AS distinct_spellings,
    STRING_AGG(DISTINCT department, ' | ') AS all_spellings
FROM users
WHERE department IS NOT NULL AND department <> ''
GROUP BY UPPER(REGEXP_REPLACE(department, '\s+', '_', 'g'))
HAVING COUNT(DISTINCT department) > 1
ORDER BY distinct_spellings DESC;

-- Check 2: Departments with leading/trailing whitespace
SELECT DISTINCT department, LENGTH(department) AS len
FROM users
WHERE department != TRIM(department)
  AND department IS NOT NULL;

-- Check 3: Departments with only whitespace
SELECT DISTINCT department
FROM users
WHERE department ~ '^\s+$';

-- Check 4: Count of distinct normalized dept_codes to be created
SELECT COUNT(DISTINCT UPPER(REGEXP_REPLACE(department, '\s+', '_', 'g'))) AS dept_codes_to_create
FROM users
WHERE department IS NOT NULL AND department <> '';

-- Check 5: Total users affected by backfill
SELECT
    COUNT(*) AS total_users,
    COUNT(department) AS users_with_dept,
    COUNT(*) - COUNT(department) AS users_without_dept
FROM users;
