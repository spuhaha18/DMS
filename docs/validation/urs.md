# User Requirements Specification

**System:** Electronic Document Management System v1
**Scope:** Regulated analysis method and validation documents

## Requirements

| ID | Requirement |
|----|-------------|
| URS-001 | Document numbers shall be generated atomically per project/type combination using a sequential counter. |
| URS-002 | Issued document numbers shall never be reused, even after rejection or cancellation. |
| URS-003 | Only `.doc` and `.docx` files shall be accepted for upload in v1. |
| URS-004 | Electronic approval shall require password re-entry at time of signing. |
| URS-005 | Electronic signatures shall bind to the specific revision and its source file SHA-256 hash at time of signing. |
| URS-006 | Official PDF generation shall record the converter version and the resulting PDF SHA-256 hash. |
| URS-007 | A document revision shall only reach Effective state after all approval tasks are approved and an official PDF hash is recorded. |
| URS-008 | Regular readers shall only be permitted to view the current effective revision; historical revisions require QA or admin access. |
| URS-009 | Every controlled official PDF view shall create a view event recording user, revision, PDF hash, IP, and user agent. |
| URS-010 | All regulated state changes shall create append-only, hash-chained audit events in the same database transaction. |
