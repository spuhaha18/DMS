# ADR 0011 — 위임(Delegation) 범위 + QA 매니저 사전 승인

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA |

## 맥락

사용자가 휴가·출장 시 결재가 막히는 운영 문제가 발생한다. 비공식적으로 비밀번호를 공유하는 위험한 관행이 있었다.

규제 요구사항:
- EU GMP Annex 11 §2: 전자서명 시스템에서 위임 책임이 명확히 기록되어야 한다.
- 21 CFR Part 11 §11.100(a): 전자서명은 개인에게 고유하며 타인이 사용할 수 없다 → **비밀번호 공유 절대 금지**

공식 위임 메커니즘이 없으면 비공식 공유가 계속된다.

## 결정

**`delegations` 도메인 풀스택 + QA 매니저 사전 승인 필수.**

### 위임 범위(scope)

| `scope_kind` | `scope_value` | 의미 |
|-------------|--------------|------|
| `APPROVAL_STEP` | role_code (예: `QA_REVIEWER`) | 특정 결재 단계만 위임 |
| `ALL` | `null` | 위임 기간 중 모든 APPROVAL work item |

`scope_value`는 자유 텍스트 입력을 허용하지 않는다. 워크플로 템플릿의 role_code 드롭다운으로만 선택 (D11). 자유 입력 허용 시 GxP 시스템에서 잘못된 범위 설정 위험.

### 대체 assignee 모델 (D5)

위임 시 **A(위임자)의 OPEN row를 CANCELLED 처리**하고 **B(대리인)의 신규 OPEN row를 생성**한다. 공동 assignee 모델은 채택하지 않는다.

```java
@Transactional
public void upsertApprovalForDelegate(WorkQueueItem aItem, User delegate, Delegation delegation) {
    // E5: A의 OPEN row CANCELLED (completedAt 필수 설정)
    aItem.cancel(delegation.getDelegatorUserId(), now());
    workQueueRepo.save(aItem);

    // B의 신규 OPEN row 생성
    WorkQueueItem bItem = WorkQueueItem.forDelegate(
        aItem, delegate.getId(), delegation.getId()
    );
    workQueueRepo.save(bItem);
    // DELEGATION_USED audit
}
```

**공동 assignee 거부 사유**: 두 사람 모두 OPEN row가 있으면 누가 서명해야 하는지 UI가 모호해진다. GxP에서 책임 소재는 명확해야 한다. 대체 assignee 모델은 B가 완료하면 해당 item이 DONE으로 종결 — 이중 처리 위험 없음.

### `markDone()` 권한 (E7)

`markDone()`은 **`assigneeUserId` (B, 현재 담당자)만 호출 가능**하다. `delegatedFromUserId` (A, 원위임자)는 불가. 이미 위임했으므로 A가 처리하면 위임 의미가 없어진다.

### QA 매니저 사전 승인 필수 (D6)

위임 신청 후 QA 매니저가 승인해야만 위임이 활성화된다. 자동 승인 없음.

```java
@PostMapping("/delegations/{id}/approve")
@PreAuthorize("hasRole('QA_MANAGER')")  // E8
public void approve(@PathVariable Long id, @RequestBody ApproveDelegationRequest req) { ... }
```

**QA 매니저 부재 시 대책 (D6 보완)**: QA 매니저 자리 비움으로 위임 자체가 막히는 상황을 방지하기 위해, QA 팀 내 **부재 시 대리 승인자**를 사전에 지정하는 운영 절차를 수립한다. 이 절차는 별도 SOP 문서(SOP-DELEG-001)로 관리. EDMS 코드에서는 `QA_MANAGER` role을 가진 사용자 누구나 승인 가능하므로 추가 코드 변경 없이 운영 절차로 해결.

### 감사 추적

모든 위임 생명주기 이벤트를 `audit_logs`에 기록:

| AuditAction | 시점 |
|------------|------|
| `DELEGATION_REQUESTED` | 위임 신청 시 |
| `DELEGATION_APPROVED` | QA 매니저 승인 시 |
| `DELEGATION_REJECTED` | QA 매니저 거부 시 |
| `DELEGATION_REVOKED` | 위임자가 취소 시 |
| `DELEGATION_USED` | 위임 활성화 → work item 대체 시 |

`work_queue` 행의 `delegated_from_user_id` 컬럼으로 "이 항목은 누구의 위임으로 생성됐는가" 추적 가능.

### 위임 만료 알림

`DelegationExpiryJob`이 APPROVED→EXPIRED 전이 수행 시 위임자와 대리인 양쪽에 `work_queue` 내 미처리 OPEN 항목이 있는지 확인하고, 있다면 알림을 발송한다. 대리인의 OPEN 항목은 만료 시 CANCELLED 처리 후 원위임자에게 OPEN 항목으로 재귀속된다 (위임 종료 후 결재 방치 방지).

### 위임 상태 전이

```
REQUESTED → APPROVED → (위임 기간 만료) → EXPIRED
          → REJECTED
APPROVED  → REVOKED (위임자 취소)
```

`DelegationExpiryJob @Scheduled`가 `valid_to < NOW()` 인 APPROVED 위임을 EXPIRED로 전이.

### UI 4 화면

1. **DelegationListView** — 내 위임 목록 (신청/활성/완료)
2. **DelegationRequestView** — 위임 신청 폼 (대리인 선택 + 기간 + scope + 사유)
3. **DelegationApprovalQueueView** — QA 매니저 전용 승인 대기 목록
4. **DelegationDetailView** — 위임 이력 상세 (audit trail 포함)

## 대안 검토

| 대안 | 거부 사유 |
|------|---------|
| 공동 assignee (A + B 모두 OPEN) | 책임 모호, 이중 처리 위험, GxP 감사 불합격 위험 |
| 자동 승인 위임 | Annex 11 §2 위반 — 위임 책임 검증 없음 |
| 위임 기능 생략 | 비밀번호 공유 관행 지속 → Part 11 §11.100(a) 위반 위험 |
| scope_value 자유 입력 | 잘못된 범위 설정 위험 (GxP 비적합) |

## 결과

- `delegations` 테이블: V28 migration
- `delegations.scope_value`: role_code 드롭다운 전용 (자유 입력 불가)
- `POST /delegations/{id}/approve`: `@PreAuthorize("hasRole('QA_MANAGER')")`
- `markDone()`: assigneeUserId(B)만 가능
- `upsertApprovalForDelegate()`: A CANCELLED → B OPEN (동일 트랜잭션)
- 운영 절차: QA 매니저 부재 시 대리 승인자 사전 지정 (SOP-DELEG-001)
