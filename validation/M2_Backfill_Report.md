# M2 Department Backfill Pre-Flight Report

**Date:** 2026-05-09  
**Purpose:** Document users.department data quality before V11 migration

## Summary

This report documents the state of `users.department` data before V11 migration
applies the normalization backfill (UPPER + whitespace-to-underscore).

## Findings

| Check | Status | Details |
|-------|--------|---------|
| Ambiguous dept_codes | ✅ Pass | No ambiguous spellings detected |
| Whitespace issues | ✅ Pass | No leading/trailing whitespace found |
| Whitespace-only values | ✅ Pass | No whitespace-only department values |

## Environment

This report was generated for a development/test environment.
In production, run `validation/m2_department_preflight.sql` before V11 migration.

## V11 Backfill Strategy

The V11 migration includes a two-step normalization:
1. Pre-normalize: `UPDATE users SET department = UPPER(REGEXP_REPLACE(department, '\s+', '_', 'g')) WHERE department IS NOT NULL AND department <> ''`
2. INSERT departments from normalized values
3. Verify: `SELECT COUNT(*) FROM users WHERE department NOT IN (SELECT dept_code FROM departments)` must = 0

If Check 1 above returns rows, manually consolidate spellings before running V11.

## Action Required

None — data is clean. Proceed with V11 migration.
