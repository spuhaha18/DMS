# Functional Specification

**System:** EDMS v1
**References:** URS

## URS-001 — Atomic Document Numbering

- Model: `DocumentNumberSequence` (project_code + document_type unique constraint)
- Service: `documents.services.register_document` — uses `select_for_update()` on the sequence row
- Test: `tests/test_numbering.py::test_number_reservation_and_audit_event_commit_together`

## URS-002 — Numbers Never Reused

- The sequence counter increments regardless of document outcome (rejection, cancellation)
- Test: `tests/test_numbering.py::test_document_numbers_are_not_reused_after_rejection_or_cancel`

## URS-003 — doc/docx Only

- Service: `documents.services._validate_doc_extension`
- Form: `documents.forms.DocumentRegistrationForm.clean_source_file`
- Test: `tests/test_upload_lifecycle.py::test_only_doc_and_docx_are_accepted`

## URS-004 — Password Re-Entry for Signing

- Service: `accounts.services.require_password_reentry`
- Called by: `approvals.services.approve_task`, `approvals.services.reject_task`
- Test: `tests/test_approval_signature.py::test_password_reentry_required_before_signing`

## URS-005 — Signature Binds to Revision and Source Hash

- Model: `approvals.models.ElectronicSignature` — stores `source_sha256` at sign time
- Service: `approvals.services.approve_task` — copies `revision.source_sha256` to signature
- Test: `tests/test_approval_signature.py::test_signature_binds_to_revision_and_source_hash`

## URS-006 — Converter Version and PDF Hash

- Model: `pdfs.models.PdfConversionJob` — stores `converter_version`
- Model: `documents.models.DocumentRevision` — stores `official_pdf_sha256`
- Service: `pdfs.services.generate_official_pdf`
- Test: `tests/test_pdf_generation.py::test_successful_conversion_records_version_hash_and_effective_status`

## URS-007 — Effective Requires Approved + PDF Hash

- Service: `documents.services.mark_effective` — validates `official_pdf_sha256` and `APPROVED` status
- Test: `tests/test_upload_lifecycle.py::test_draft_uploaded_cannot_jump_to_effective_without_pdf`

## URS-008 — Reader Access Control

- Service: `viewer.services.resolve_viewable_revision` — non-QA/admin users get only current effective revision
- Test: `tests/test_viewer_authorization.py::test_regular_reader_cannot_view_historical_revision`

## URS-009 — View Events

- Model: `viewer.models.ViewEvent`
- Service: `viewer.services.record_view`
- Test: `tests/test_viewer_authorization.py::test_every_view_creates_view_event`

## URS-010 — Append-Only Hash-Chained Audit

- Model: `audit.models.AuditEvent` — `save()` guard prevents updates; `prev_hash`/`event_hash` chain
- Service: `audit.services.append_event`, `audit.services.validate_hash_chain`
- Tests: `tests/test_audit.py`
