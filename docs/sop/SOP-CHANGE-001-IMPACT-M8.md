# SOP-CHANGE-001 변경통제 영향평가서 — M8 Work Queue + Notification Delivery + SignatureDialog + Delegation

| 문서번호 | SOP-CHANGE-001-IMPACT-M8 |
|---------|--------------------------|
| 버전 | 1.0 |
| 작성일 | 2026-05-13 |
| 작성자 | IT 개발팀 |
| 변경 분류 | **Major** |
| 상태 | 초안 — 3명 서명 후 PR2 착수 가능 |

> **PR 머지 게이트 안내:**
> - **PR1 (이 문서 포함)**: 코드 변경 없음 — 아키텍처 문서 및 영향평가서만 포함. 시스템 상태 변경이 없으므로 서명 전 main 머지 허용. (SOP-CHANGE-001 §4.3: "변경 내용이 없는 설계 문서는 설계 리뷰 단계로 간주하며 변경통제 실행 전에 작성 가능하다")
> - **PR2 이후 (마이그레이션, 코드)**: 아래 승인란 **3명 서명 완료** 후에만 착수 가능. 서명 없이 PR2+를 시작하는 것은 21 CFR Part 11 §11.10(d) 및 EU GMP Annex 11 §4.2 위반임.

---

## 1. 변경 식별

### 1.1 변경 유형

**Major** — 사유:
- 신규 도메인 4종 신설: `work_queue`, `notification/outbox/dlq`, `delegation`, `auth session state`
- 신규 외부 의존성 추가: `spring-boot-starter-mail` + `GreenMail` (테스트 컨테이너)
- 신규 API surface 9 path (work-queue, delegations, auth/session-state, notifications 갱신)
- Flyway schema migration 5개 (V25–V29)
- 21 CFR Part 11 §11.10(b) 강화 — SignatureDialog 본문 확인 강제
- Annex 11 §2 위임 책임 기록 신규 도입

### 1.2 변경 제목

결재 인박스(Work Queue) + 알림 배달(Outbox/DLQ) + SignatureDialog + 위임(Delegation) 풀스택 구현

### 1.3 변경 번호

M8

---

## 2. 변경 범위

### 2.1 신규 도메인

| 도메인 | 신규 테이블 | 설명 |
|--------|-----------|------|
| work_queue | `work_queue` | kind ∈ {APPROVAL, TRAINING, PERIODIC_REVIEW, READACK}, state ∈ {OPEN, DONE, CANCELLED, EXPIRED}. M8은 APPROVAL만 데이터 투입, 나머지 3종 스키마만 준비 |
| notification | `notifications`, `notification_outbox`, `notification_dlq`, `notification_event_codes`, `notifications_archived` | in-app 보기 기록 + 전송 큐 + DLQ + 코드 참조 테이블 + archived |
| delegation | `delegations` | 위임자/대리인/범위/QA승인/상태 관리 |
| auth | 스키마 변경 없음 | `/auth/session-state` endpoint 신규 |

### 2.2 신규 API endpoints

| 경로 | 메서드 | 용도 |
|------|--------|------|
| `/api/v1/work-queue` | GET | 내 할 일 목록 |
| `/api/v1/work-queue/{id}/done` | POST | 완료 처리 |
| `/api/v1/work-queue/{id}/cancel` | POST | 취소 처리 |
| `/api/v1/delegations` | GET, POST | 위임 목록/신청 |
| `/api/v1/delegations/{id}` | GET, DELETE | 위임 조회/취소 |
| `/api/v1/delegations/{id}/approve` | POST | QA 매니저 승인 |
| `/api/v1/auth/session-state` | GET | 첫 서명 여부 확인 |

### 2.3 Deprecated endpoints (1 마일스톤 후 제거)

- `GET /api/v1/workflow/my-pending` → `GET /api/v1/work-queue?kind=APPROVAL&state=OPEN` 으로 이전 (ADR 0009)
- `GET /api/v1/notifications/stream` (SSE) → polling 30s (ADR 0006)

### 2.4 외부 의존성 추가

- `spring-boot-starter-mail` — SMTP 발송 어댑터. 운영 활성화는 `notification.email.enabled=false` 게이트로 분리 (ADR 0007)
- `com.icegreen:greenmail-spring` (테스트 전용) — GreenMail Testcontainer로 SMTP 통합 테스트

### 2.5 제외 범위

- 워크플로 도메인 핵심 로직 (`WorkflowService`, `WorkflowController.submit/sign/reject`) — 무수정
- `audit_logs`, `signature_manifests` 테이블 — UPDATE/DELETE 절대 금지 (변경 없음)
- MinIO 버킷 구성 — 변경 없음
- 기존 `EmailNotificationService`, `LogEmailNotificationService` — 즉시 삭제하지 않고 마이그레이션 기간 보존

---

## 3. 영향 평가

### 3.1 규제 영향

| 규정 | 조항 | 영향 | 조치 |
|------|------|------|------|
| 21 CFR Part 11 | §11.10(b) | SignatureDialog — 본문 확인 후에만 서명 버튼 활성화 강제 | `@pdf-read-complete` 이벤트 + OPEN state 조건 (ADR 0008/D8) |
| 21 CFR Part 11 | §11.10(d) | audit_logs 12종 신규 추가 | `AuditAction` enum 확장, append-only 유지 |
| 21 CFR Part 11 | §11.10(e) | audit_logs 보안 — 변경 불가 | UPDATE/DELETE 금지 정책 유지 |
| 21 CFR Part 11 | §11.200(a) | 첫 로그인 후 비밀번호 변경 UX | `/auth/session-state` endpoint + Pinia 캐시 |
| EU GMP Annex 11 | §2 | 위임 책임 기록 신규 | `delegations` 도메인 + `DELEGATION_*` audit (ADR 0011) |
| EU GMP Annex 11 | §9 | 감사추적 완전성 | work_queue 상태 전이마다 audit 기록 |
| EU GMP Annex 11 | §14 | 검토 확인 강화 | SignatureDialog 본문 확인 흐름 |
| ALCOA+ Enduring | — | notifications hard delete 금지 | read 90일 후 archived 이전(ADR 0005), 5년 보존 |

### 3.1.1 사용자 교육·훈련 영향 (21 CFR Part 11 §11.10(i) / Annex 11 §2)

| 대상 | 교육 내용 | 시점 |
|------|---------|------|
| 전체 사용자 | Work Queue 인박스 사용법, SignatureDialog 본문 확인 절차 | PR6/PR7 배포 전 |
| QA 매니저 | 위임 신청 승인 절차, 부재 시 대리 승인자 지정 | PR5 배포 전 |
| IT팀 | OutboxDispatcher 모니터링, DLQ 알림 확인 절차 | PR4 배포 전 |

교육 완료 기록은 별도 교육일지로 관리 (훈련기록부 TRG-M8-001).

### 3.2 검증(V&V) 영향

| 항목 | 영향 | 조치 |
|------|------|------|
| IQ (설치 적격성) | 신규 테이블 V25–V29, 신규 Spring 의존성 | IQ 체크리스트 신규 항목 추가 |
| OQ (운영 적격성) | 9개 신규 API, work_queue 상태 전이, outbox 재시도, delegation 승인 흐름 | OQ 케이스 OQ-WQ-101~110, OQ-NTFY-101~115, OQ-DLG-101~108 (총 33개 신규) |
| PQ (성능 적격성) | OutboxDispatcher 10s 주기 폴링, work_queue 동시 접근 | PQ 시나리오 2개 신규 |

### 3.3 데이터 영향

- 신규 테이블만 추가. 기존 테이블 스키마 수정 없음.
- `work_queue.state` 전이 이력은 audit_logs로 추적.
- `notifications` → `notifications_archived` 이전은 90일 경과 후 배치 처리. 하드 삭제 없음.
- 위임 기록(`delegations`)은 상태 종료 후에도 삭제 금지 (이력 보존).

---

## 4. 위험 평가

| 위험 | 등급 | 대책 |
|------|------|------|
| SMTP relay 미준비 시 이메일 미발송 | **중간** | `notification.email.enabled=false` 게이트. `LogEmailChannel`이 로그로 대체. 운영 활성화는 별도 단계 (ADR 0007) |
| OutboxDispatcher 실패 시 알림 누락 | **중간** | 최대 3회 재시도(지수 백오프 1m/5m/30m) + `notification_dlq` 격리 + `NOTIFICATION_DELIVERY_FAILED` audit. 크래시 후 재시작 시 SENDING stuck 행 10분 경과 후 FAILED 복구 (ApplicationRunner) (ADR 0010) |
| Delegation race — 동시 승인 요청 | **중간** | `delegations` 테이블 낙관적 락 + QA 매니저 단일 승인 게이트 |
| WorkQueueProjector 누락 — work_queue 미생성 | **중간** | `@TransactionalEventListener(REQUIRES_NEW)` + 통합 테스트 `WorkQueueProjectorIT` |
| polling 30s 부하 (50명 동시 접속) | **낮음** | DB index + `@PageableDefault(size=20, max=100)` 제한. PQ에서 검증 |
| 대체 assignee 중복 OPEN row | **낮음** | `upsertApprovalForDelegate()`가 A의 OPEN row를 CANCELLED 처리 후 B 신규 생성 (동일 트랜잭션, E5) |
| 서명 버튼 단일 페이지 PDF 미활성화 | **낮음** | `totalPages === 1` 시 `pdf-read-complete` 즉시 emit (E10) |
| `notification_outbox.payload_json` 비대화 | **낮음** | template key + params만 저장, `CHECK (octet_length < 65536)` 제약 (E4) |

---

## 5. 검증 계획

### 5.1 OQ 케이스 예약

**Work Queue (OQ-WQ-101~110)**

| ID | 시나리오 |
|----|---------|
| OQ-WQ-101 | 문서 제출 → APPROVAL work item OPEN 생성 확인 |
| OQ-WQ-102 | 서명 완료 → work item DONE 전이 |
| OQ-WQ-103 | 결재 반려 → work item CANCELLED 전이 |
| OQ-WQ-104 | 기한 만료 → work item EXPIRED 전이 (스케줄러) |
| OQ-WQ-105 | 위임 활성 시 대체 assignee로 OPEN 행 전환 |
| OQ-WQ-106 | `GET /work-queue?kind=APPROVAL&state=OPEN` 권한 필터 확인 |
| OQ-WQ-107 | `POST /work-queue/{id}/done` — assigneeUserId만 가능 |
| OQ-WQ-108 | `GET /work-queue?kind=APPROVAL&state=OPEN` 페이지 크기 상한 100 |
| OQ-WQ-109 | audit_logs — WORK_QUEUE_ITEM_OPENED/DONE/CANCELLED 기록 |
| OQ-WQ-110 | deprecated `GET /workflow/my-pending` Sunset 헤더 확인 |

**Notification Outbox (OQ-NTFY-101~115)**

| ID | 시나리오 |
|----|---------|
| OQ-NTFY-101 | 이벤트 발행 → outbox PENDING 행 생성 (AFTER_COMMIT) |
| OQ-NTFY-102 | OutboxDispatcher 10s 후 SENDING → SUCCESS 전이 |
| OQ-NTFY-103 | 채널 실패 1회 → 재시도 1m 후 |
| OQ-NTFY-104 | 채널 실패 3회 → DLQ 이동 + NOTIFICATION_DELIVERY_FAILED audit |
| OQ-NTFY-105 | `GET /notifications` — 페이지 크기 상한 100 |
| OQ-NTFY-106 | `PUT /notifications/{id}/read` — isRead true 전환 |
| OQ-NTFY-107 | in-app 알림 읽음 90일 경과 → archived 이전 (배치) |
| OQ-NTFY-108 | APPROVAL audit_logs 영구 보존 확인 |
| OQ-NTFY-109 | ShedLock — 다중 인스턴스 중 1개만 OutboxDispatcher 실행 |
| OQ-NTFY-110 | 크래시 후 재시작 → SENDING stuck 10분 FAILED 복구 |
| OQ-NTFY-111 | LogEmailChannel — SMTP disabled 시 로그 기록 |
| OQ-NTFY-112 | SmtpEmailChannel — GreenMail 수신 확인 |
| OQ-NTFY-113 | `notification_outbox.payload_json` 65536 바이트 초과 거부 |
| OQ-NTFY-114 | `notifications_archived` — archived 후 원본 삭제 확인 |
| OQ-NTFY-115 | NOTIFICATION_ARCHIVED audit — 일 1건(개수만) |

**Delegation (OQ-DLG-101~108)**

| ID | 시나리오 |
|----|---------|
| OQ-DLG-101 | 위임 신청 → REQUESTED 상태 + DELEGATION_REQUESTED audit |
| OQ-DLG-102 | QA 매니저 승인 → APPROVED + DELEGATION_APPROVED audit |
| OQ-DLG-103 | QA 매니저 거부 → REJECTED + DELEGATION_REJECTED audit |
| OQ-DLG-104 | 위임자 취소 → REVOKED + DELEGATION_REVOKED audit |
| OQ-DLG-105 | 위임 활성 → 대리인 work item OPEN 생성 + DELEGATION_USED audit |
| OQ-DLG-106 | 위임 기간 만료 → EXPIRED 전이 (스케줄러) |
| OQ-DLG-107 | `POST /delegations/{id}/approve` — QA_MANAGER role만 가능 |
| OQ-DLG-108 | scope_value 드롭다운 — 워크플로 템플릿 role_code 기반 |

### 5.2 PQ 시나리오

| 시나리오 | 기준 |
|---------|------|
| 50명 동시 work-queue 폴링 (30s 주기) | DB CPU < 30%, 응답 < 500ms |
| OutboxDispatcher 100건 배치 처리 | 전체 처리 완료 ≤ 30초 (채널 mock) |

---

## 6. 롤백 계획

| PR | 롤백 방법 | 비가역 여부 |
|----|-----------|------------|
| PR1 (ADR/SOP) | 문서 삭제 — 코드 영향 없음 | 가역 |
| PR2 (V25-V29 migrations) | Flyway repair + 테이블 DROP (데이터 없음) + `spring-boot-starter-mail` 제거 | 가역 |
| PR3 (work_queue backend) | `workqueue.enabled=false` feature flag → PR 리버트 | 가역 |
| PR4 (notification/outbox) | `notification.in-app.enabled=false` + `notification.email.enabled=false` → PR 리버트 | 가역 |
| PR5 (delegation backend) | `delegation.enabled=false` → PR 리버트 | 가역 |
| PR6 (frontend work_queue + bell) | 프론트엔드 PR 리버트. 백엔드 데이터 영향 없음 | 가역 |
| PR7 (SignatureDialog + delegation UI) | 프론트엔드 PR 리버트 | 가역 |

**notifications_archived 이전 후 롤백:** archived 이전은 배치로 원본 삭제 → 단순 롤백 불가. 단, archived 테이블 보존으로 데이터 손실 0건. 비상 복구는 archived → notifications 역이전 스크립트(SOP-RCY-001로 관리 예정 — 해당 SOP는 PR4 배포 전 IT팀이 작성 완료해야 한다. 미작성 시 PR4 배포 금지).

---

## 7. 승인란

**Major 변경 — QA 매니저 + IT팀 리드 + 검증팀 리드 3-way 서명 필요.**
이 문서에 서명 완료 후 PR2(마이그레이션) 작업 착수 가능.
PR1(ADR/SOP 문서)은 서명 전에도 작성 가능 — 코드 변경 없음.

| 역할 | 성명 | 서명 | 날짜 |
|------|------|------|------|
| QA 매니저 | | | |
| IT팀 리드 | | | |
| 검증팀 리드 | | | |

> **주의:** Major 변경이므로 Minor 승인 절차(QA 매니저 1명)로는 부족합니다. (SOP-CHANGE-001 §4.4)
