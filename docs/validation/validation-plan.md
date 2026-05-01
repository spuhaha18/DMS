# Validation Plan

**System:** EDMS v1

## Automated Verification (Developer)

Run after every change:

```bash
python3 manage.py check
pytest -q
python3 manage.py backup_smoke
```

Expected: all tests pass, no system check issues, backup smoke passes.

## Release Gate: Golden-File PDF Samples

The automated test suite uses mocked LibreOffice output. Before release acceptance, five representative `.docx` samples (provided by the business) must be converted with production LibreOffice and the resulting PDFs verified against stored golden hashes.

Status: **Pending sample collection** (see TODOS.md).

## Manual Smoke Flow

Performed once per release candidate on a clean database:

1. QA creates a project code via admin.
2. QA creates a document type via admin.
3. QA creates an active approval route template with at least one step.
4. Researcher uploads a `.docx` source through the Register UI.
5. System issues a non-reused document number.
6. Approver opens the pending task and approves with password re-entry.
7. QA runs `python3 manage.py generate_official_pdf <revision_id> --actor <qa_username>`.
8. Reader opens the current effective official PDF in the controlled viewer.
9. QA confirms the view event and audit trail entries in the evidence package.

## Manual Smoke Checklist

- [ ] QA creates project code.
- [ ] QA creates document type.
- [ ] QA creates active route template with at least one step.
- [ ] Researcher uploads a `.docx` source.
- [ ] System issues a non-reused document number.
- [ ] Approver approves with password re-entry.
- [ ] QA runs official PDF generation.
- [ ] Reader opens current effective official PDF in the controlled viewer.
- [ ] QA confirms view event and audit trail entries in the evidence package.

## Backup/Restore Smoke

```bash
python3 manage.py backup_smoke
```

Verifies SHA-256 integrity for all stored files and validates the audit hash chain. Must pass before any production deployment.
