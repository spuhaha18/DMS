# Access Control Matrix

**System:** EDMS v1

| Action | Researcher | Reviewer | Approver | QA | Reader | Admin |
|--------|-----------|----------|----------|----|--------|-------|
| Upload `.doc/.docx` source | ✓ | | | ✓ | | ✓ |
| View document register list | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View document detail | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Submit for approval | ✓ | | | ✓ | | ✓ |
| Approve/reject approval task | | ✓ | ✓ | | | ✓ |
| View current effective PDF | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View historical PDF revisions | | | | ✓ | | ✓ |
| Generate official PDF | | | | ✓ | | ✓ |
| View evidence package | | | | ✓ | | ✓ |
| View audit trail | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Configure project codes | | | | ✓ | | ✓ |
| Configure document types | | | | ✓ | | ✓ |
| Configure approval route templates | | | | ✓ | | ✓ |
| Manage user accounts | | | | | | ✓ |

*Roles are implemented as Django Groups. Users may have multiple roles.*
