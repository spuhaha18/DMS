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

## DEV-LCY-001: workflow_instances.version_id UNIQUE 제약 제거

**날짜:** 2026-05-10
**DS 참조:** DS §4.2 (workflow_instances 테이블)
**편차 내용:** DS §4.2의 `workflow_instances.version_id UNIQUE` 제약 제거
**사유:** 반려 후 재제출 시 새 워크플로 인스턴스 생성을 허용하면서 이전 시도의 `step_instances` 행을 audit 증거로 보존해야 함 (21 CFR Part 11 §11.10(e) 결재 시도 추적)
**대체 보장:** `uq_one_active_workflow_per_version` 부분 unique 인덱스로 "활성 인스턴스 1개" 보장
**조치:** DS 차기 개정 시 반영 예정
