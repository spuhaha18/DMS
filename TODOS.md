# EDMS v1 — Outstanding Items

## Implemented

Validation documentation is now complete and lives in `docs/validation/`:
- [URS](docs/validation/urs.md) — User Requirements Specification
- [Functional Specification](docs/validation/functional-specification.md)
- [Risk Assessment](docs/validation/risk-assessment.md)
- [Validation Plan](docs/validation/validation-plan.md)
- [Electronic Signature Policy](docs/validation/electronic-signature-policy.md)
- [Access Control Matrix](docs/validation/access-control-matrix.md)
- [Audit Trail Review SOP](docs/validation/audit-trail-review-sop.md)

## Required Business Inputs Before Release

The following items require human decisions and cannot be completed by the development team alone:

1. **Collect five representative sample documents** — Provide `.docx` examples of analysis method and validation documents for golden-file PDF regression testing (see Validation Plan).
2. **Confirm first production approval route templates** — Define which groups and signature meanings apply to each document type in production.
3. **Approve the Electronic Signature Policy** — QA manager sign-off required before regulated use begins.
4. **Approve watermark text** — Confirm the watermark template in `config/settings.py` (`EDMS_WATERMARK_TEMPLATE`) meets organizational requirements.
