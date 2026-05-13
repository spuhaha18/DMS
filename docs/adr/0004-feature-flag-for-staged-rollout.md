# ADR 0004 — Feature flag로 단계적 배포

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA |

## 맥락

M7.5는 4개 PR로 분할 배포된다:
- PR2: MinIO 모드 전환 + `ProjectBasedRetentionResolver` + PDF 파이프라인 하드코딩 제거.
- PR3: outbox 워커 + 관리 UI + 문서.

PR2가 먼저 머지되면, PR3 전까지 운영자가 상태 전이(APPROVE/TERMINATE)를 할 화면이 없다. 그러나 PR2의 코드가 이미 배포되어 있으면 누군가 API 직접 호출로 상태 전이를 유발할 수 있고, 그 시점에 outbox가 아직 없으므로 MinIO 연장이 발생하지 않는 중간 상태가 된다.

## 결정

**`retention.feature.project-based-enabled` flag 도입(기본값 `false`).**

동작:
- `false`: `ProjectBasedRetentionResolver.resolveYears()` 가 `fallback-years(30)`를 반환. PDF 파이프라인도 30년 fallback.
- `true`: SOP Table 1 기반 계산 활성화.

전환 시점: PR3 머지 + 운영 stage IQ 통과 후 운영자가 `application.yml` 수정 → 재배포.

전환 감사: `true`로 바뀌는 순간 `AuditAction.RETENTION_FEATURE_TOGGLED` 행 1건 자동 기록(서비스 시작 시 flag 값을 audit에 기록하는 구현 추가).

## Hikari Connection Pool 영향 메모

`AuditService`는 내부적으로 이미 `REQUIRES_NEW`를 사용하고 별도 audit DataSource를 가진다. 따라서 메인 트랜잭션 + audit 트랜잭션의 pool 분리는 이미 설계에 반영되어 있다. outbox 워커의 `@Scheduled` 메서드는 별도 스레드풀에서 실행되므로 메인 요청 처리 pool과 경합하지 않는다. 동시 APPROVE 부하 테스트(PQ)에서 pool exhaustion 여부를 확인한다.

## 결과

- `application.yml`에 `retention.feature.project-based-enabled: false` + `retention.fallback-years: 30` 추가.
- PR2 머지 후 운영 환경에서 flag=false 확인 → 기존 동작 동일.
- PR3 머지 + IQ 통과 → 운영자 flag=true 전환 → audit 자동 기록.
- **이 flag는 영구 feature toggle이 아니다** — M7.5 안정화 확인 후 M9에서 제거 검토.
