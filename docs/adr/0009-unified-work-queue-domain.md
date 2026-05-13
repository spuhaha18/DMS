# ADR 0009 — 통합 Work Queue 도메인

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA |

## 맥락

DMS에는 사용자가 처리해야 할 작업이 여러 종류 존재한다:
- **APPROVAL**: 워크플로 결재 대기
- **TRAINING**: 신규 SOP 읽기 확인 (M9 예정)
- **PERIODIC_REVIEW**: 정기 문서 검토 (M10 예정)
- **READACK**: 긴급 공지 읽기 확인 (M10 예정)

기존 접근방식(v1 notification-center): `notification.type` 필터로 결재 대기를 식별. 문제점:
1. notifications는 delivery 레이어 — 결재 SSoT가 될 수 없다.
2. 4개 kind를 각각 별도 도메인(테이블 4개, API 4세트)으로 구현하면 UI 일관성과 알림 채널 연결이 복잡해진다.
3. M4 워크플로 도메인의 `GET /workflow/my-pending`은 APPROVAL 전용 — 나머지 kind를 수용할 수 없다.

## 결정

**단일 `work_queue` 테이블 + `kind` discriminator.**

### 스키마 핵심 컬럼

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `kind` | VARCHAR(40) | `APPROVAL \| TRAINING \| PERIODIC_REVIEW \| READACK` |
| `state` | VARCHAR(20) | `OPEN \| DONE \| CANCELLED \| EXPIRED` |
| `assignee_user_id` | BIGINT FK | 처리 담당자 |
| `delegated_from_user_id` | BIGINT FK NULL | 위임 원위임자 (ADR 0011) |
| `source_type` | VARCHAR(40) | 원천 도메인 식별자 (`WORKFLOW_STEP`) |
| `source_id` | BIGINT | 원천 도메인 레코드 ID |
| `related_document_id` | BIGINT FK NULL | 연결 문서 |
| `link_path` | VARCHAR(500) | 프론트엔드 라우트 경로 |

### M8 데이터 투입 범위

M8에서는 **APPROVAL kind만** 데이터를 투입한다. `WorkQueueProjector`가 `WorkflowSubmittedEvent`를 받아 `work_queue` 행을 INSERT. 나머지 3 kind는 스키마 + enum + `WorkQueueKindHandler` 인터페이스만 준비 (M9~M10 인입).

### WorkQueueProjector

```java
@Component
public class WorkQueueProjector {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)  // E3: 별도 트랜잭션
    public void onSubmitted(WorkflowSubmittedEvent e) { /* upsert OPEN */ }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void onSigned(WorkflowSignedEvent e) { /* mark DONE */ }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void onRejected(WorkflowRejectedEvent e) { /* mark CANCELLED */ }
}
```

`REQUIRES_NEW`를 사용하는 이유: `AFTER_COMMIT` 핸들러는 원 트랜잭션 밖에서 실행된다. 별도 트랜잭션이 없으면 `DelegationService` 호출 등 부수 작업이 원 커밋과 묶이지 않아 부분 실패 시 롤백 불가 (E3).

### `WorkflowController.my-pending` deprecated 처리

```java
@Deprecated
@GetMapping("/workflow/my-pending")
// + Sunset 헤더, OpenAPI deprecated: true
```

`GET /api/v1/work-queue?kind=APPROVAL&state=OPEN`이 새 SSoT. Deprecation period: 1 마일스톤(M9 배포 전 제거 목표).

## 대안 검토

| 대안 | 거부 사유 |
|------|---------|
| 4개 분리 도메인(테이블 4개) | UI 불일관성, 알림 채널 연결 4벌 구현, kind 추가 시 신규 도메인 전체 구현 |
| `notification.type`으로 결재 SSoT 대체 | notification은 delivery 레이어 — 비즈니스 상태 SSoT 역할 부적합 |
| `WorkflowStepInstance` 직접 쿼리 | 워크플로 도메인 내부 노출, 향후 kind 확장 불가 |

## 결과

- `work_queue` 테이블: V25 migration (ADR 0010 outbox와 함께 V26)
- API SSoT: `GET /api/v1/work-queue?kind=APPROVAL&state=OPEN`
- `WorkQueueProjector`: 이벤트 기반 work_queue 동기화
- `GET /workflow/my-pending`: deprecated, 1 마일스톤 후 제거
- M9+: TRAINING/PERIODIC_REVIEW/READACK kind 핸들러 추가
