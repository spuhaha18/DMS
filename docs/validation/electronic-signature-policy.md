# Electronic Signature Policy

**System:** EDMS v1

## 1. Unique Account Requirement

Each signer must have a unique user account. Shared accounts are prohibited. Account creation requires QA or admin approval.

## 2. Password Re-Entry

Every electronic signature requires the signer to re-enter their current password at the time of signing. This re-entry is verified server-side before the signature record is created.

## 3. Signature Meaning

Each signature carries an explicit meaning (e.g., "Review", "Approval") defined in the approval route template. The meaning is captured in the `ElectronicSignature` record and cannot be changed after signing.

## 4. Binding to Document Revision

Every signature is cryptographically bound to the source file SHA-256 hash of the specific revision being signed. If the source file changes, a new revision and new approval tasks are required.

## 5. Account Lifecycle

- Accounts must be deactivated (not deleted) when a signer leaves the organization.
- Deactivated accounts cannot create new signatures (`is_active` check enforced by `require_password_reentry`).
- Historical signatures from deactivated accounts remain valid records.

## 6. Signer Responsibility

By entering their password and submitting an approval decision, the signer certifies that they have reviewed the document and that their decision reflects their professional judgment.
