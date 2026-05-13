# ADR 0005 — 알림 보존 정책: kind별 분리 + archived 이전

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA |

## 맥락

GxP EDMS에서 알림(notification)은 두 가지 성격이 혼재한다:

1. **규제 증거가 필요한 알림** — 결재(APPROVAL) 알림은 "누가 언제 결재 요청을 받았고 처리했는가"를 추적해야 한다. 21 CFR Part 11 §11.10(d), EU GMP Annex 11 §9.
2. **UX 편의 알림** — 읽음 처리 후 수개월이 지나면 실용적 가치가 없다.

`notifications` 테이블을 영구 보존하면 대형 EDMS에서 수백만 행이 누적된다. 하드 삭제(DELETE)는 GxP 감사 원칙(ALCOA+ Enduring)에 위배된다.

## 결정

**kind별 증거 전략 분리 + archived 이전(하드 삭제 없음).**

### APPROVAL kind

결재 알림 자체의 증거는 **`audit_logs`로 영구 보존**한다. `WorkflowSignedEvent` → `WorkflowService` → `AuditAction.WORK_QUEUE_ITEM_DONE` 등으로 이미 기록된다. `notifications` 행은 UI 표시용 캐시이므로 90일 후 `notifications_archived`로 이전해도 규제 증거에 공백이 없다.

### TRAINING / PERIODIC_REVIEW / READACK (M9+)

동일 정책 적용 예정. M9 설계 시 kind별 보존일 오버라이드 필요 시 `notification_event_codes.retention_days` 컬럼 추가로 확장 가능.

### archived 이전 정책

- `notifications.is_read = TRUE` + `notifications.created_at < NOW() - INTERVAL '90 days'` → `notifications_archived`로 이전
- 이전 후 원본 행 삭제 (이전 완료 확인 후)
- `notifications_archived`는 **5년 보존** 후 vacuum
- 이전 시 `AuditAction.NOTIFICATION_ARCHIVED` audit 1건 기록 (행 수만, 개별 알림 ID 나열 불필요)
- 이전 작업은 `NotificationArchiveJob @Scheduled(cron)` + ShedLock leader election. `SELECT FOR UPDATE SKIP LOCKED`로 동시 실행 경쟁 방지 (E9).

### 미읽음 알림

`is_read = FALSE`인 알림은 90일이 경과해도 이전하지 않는다. 사용자가 읽지 않은 알림을 강제 삭제하면 UX + 감사 양면에서 문제.

## 대안 검토

| 대안 | 거부 사유 |
|------|---------|
| 영구 보존 (no delete, no archive) | 테이블 무한 성장, 폴링 성능 저하 |
| 하드 삭제 (DELETE) | ALCOA+ Enduring 위반, GxP 감사 불합격 |
| 별도 큐 서비스 (Kafka 등) | 폐쇄망 인프라 미비, 복잡도 과도 |

## 결과

- `notifications` 테이블: 활성 + 읽음 90일 이내 행만 유지
- `notifications_archived`: 5년 보존 cold storage 역할
- APPROVAL 규제 증거: `audit_logs` 영구 보존으로 이중 커버
- `application.yml`: `notification.retain-read-days: 90`, `notification.archive-cron: "0 30 1 * * ?"` (매일 01:30 KST)
