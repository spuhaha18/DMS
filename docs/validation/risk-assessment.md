# Risk Assessment

**System:** EDMS v1

| Risk | Severity | Likelihood | Detection | Mitigation | Residual Risk |
|------|----------|------------|-----------|------------|---------------|
| Duplicate document number | High | Low | `unique` DB constraint + test | Atomic `select_for_update` on sequence row | Very Low |
| Signature applied to wrong file | High | Low | SHA-256 binding test | `source_sha256` copied at sign time, not post-hoc | Very Low |
| Rejected signature reused on new revision | High | Low | New revision creates new tasks | Signatures are `OneToOne` to `ApprovalTask`; new revision = new tasks | Very Low |
| PDF conversion layout drift | Medium | Medium | Golden-file test (release gate) | Converter version recorded per job; mocked unit tests verify hash flow | Medium (pending golden-file samples) |
| Unauthorized historical PDF access | High | Low | Viewer authorization test | `resolve_viewable_revision` enforces QA/admin gate | Very Low |
| Audit tampering | High | Very Low | `validate_hash_chain` + backup smoke | Append-only model guard; SHA-256 hash chain | Very Low |
| Missing view event | High | Very Low | View event test | `record_view` called unconditionally before PDF render | Very Low |
