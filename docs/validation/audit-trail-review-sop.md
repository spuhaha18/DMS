# Audit Trail Review SOP

**System:** EDMS v1

## 1. Review Frequency

The audit trail shall be reviewed:
- Monthly by the QA role as part of routine document control.
- Immediately following any suspected unauthorized access or data integrity concern.
- Before release of a new system version.

## 2. How to Access

Navigate to **Audit** in the EDMS header, or go to `/audit/`. Filter by document number, username, event type, or date range.

## 3. Filter Guidance

| Filter | Use Case |
|--------|----------|
| Document number | Trace all events for a specific controlled document |
| Username | Review activity for a specific user account |
| Event type | Filter to `config.changed` for configuration reviews |
| Date range | Scope review to a reporting period |

## 4. Expected Event Types

| Event Type | Trigger |
|------------|---------|
| `document.registered` | New document registered |
| `document.submitted_for_approval` | Revision submitted for review |
| `approval.task_approved` | Approver signs approval |
| `approval.task_rejected` | Approver rejects revision |
| `document.approved` | All approval tasks complete |
| `document.effective` | Official PDF generated and revision made effective |
| `pdf.generated` | Official PDF successfully generated |
| `viewer.pdf_viewed` | Controlled PDF opened by a user |
| `config.changed` | QA/admin changes project code, document type, or route template |

## 5. Hash Chain Integrity

Run `python3 manage.py backup_smoke` to verify the audit hash chain. Any reported error must be investigated and documented before further regulated activities proceed.

## 6. Exception Handling

If a hash chain error or unexpected event is found:
1. Record the finding with timestamp, affected event IDs, and description.
2. Escalate to the system administrator.
3. Do not modify any records.
4. Document the investigation and resolution in a separate QA record.

## 7. Evidence Package Review

For each approved document, the evidence package (`/documents/<id>/evidence/`) provides an aggregated view of approval tasks, signatures, PDF conversion jobs, view events, and audit events. Review this package when releasing a document to confirm completeness.
