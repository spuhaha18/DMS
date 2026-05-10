# Deviations from Design Specification

## DEV-DOC-001: BR-DOC-008 체크아웃 잠금 M4 연기

**Deviation:** BR-DOC-008 checkout locking mechanism deferred to M4  
**Reason:** Cannot implement independently from workflow step assignee (M4 LifecycleStateMachine)  
**Impact:** In M3, only the document owner can re-upload a DRAFT file. Concurrent Author access is prevented at the service layer (owner check), not via an explicit checkout lock.  
**Resolution:** Implement in M4 together with WorkflowStepInstance assignee logic.  
**Approved:** Pending formal approval

## DEV-DOC-002: departments Master Table DS §4.2 미등재 신설

**Deviation:** New `departments` + `department_aliases` tables added, not in DS §4.2  
**Reason:** HRIS integration possibility identified during M3 planning; dept_code 1:N dept_name requirement  
**Impact:** `documents.department`, `users.department`, `permissions.department` all store dept_code as anchor. M2 data backfill performed in V11.  
**Resolution:** Document in next DS revision. GitHub Issue #regulatory-traceability to track.  
**Approved:** Pending formal approval
