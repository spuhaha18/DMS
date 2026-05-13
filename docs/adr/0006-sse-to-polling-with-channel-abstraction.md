# ADR 0006 — SSE 대신 30초 폴링 채택 + 채널 추상화

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT |

## 맥락

M8 설계 초안(DS §5.6)은 Server-Sent Events(SSE)를 실시간 알림 push transport로 명세했다. 그러나 배포 환경 제약이 존재한다:

1. **폐쇄망 Nginx/L7 LB 미검증** — SSE는 long-lived HTTP connection이 필요하다. 사내 L7 proxy의 idle timeout 설정이 30~60초로 짧을 수 있고, 이를 수정하려면 별도의 인프라 변경이 필요하다.
2. **Tomcat idle timeout** — Spring의 기본 Tomcat은 SSE keep-alive 튜닝이 별도로 필요하다.
3. **`maximumSessions(1)` 충돌** — 단일 세션 정책에서 SSE connection이 세션을 점유하는 방식이 검증되지 않았다.
4. **50명 소규모 시스템** — SSE의 실시간성이 주는 UX 이점이 검증 비용을 정당화하지 않는다.

## 결정

**30초 폴링 + `NotificationChannel` 인터페이스 추상화.**

### 폴링 스펙

- 클라이언트: `setInterval(fetch, 30_000)` — `WorkQueueStore.startPolling()`, `NotificationStore.startPolling()`
- 서버: `GET /api/v1/work-queue?state=OPEN` (work queue), `GET /api/v1/notifications?isRead=false` (bell badge 개수)
- 즉시 refetch: 사용자가 work item 클릭 시 → 서버 상태와 즉시 동기화
- 폴링 실패 2회 연속 → `UiBanner` "대기 목록을 불러올 수 없습니다. 마지막 성공: {lastSuccessAt}" 표시 (D10)

### UX 영향

평균 알림 지연 15초 (30초 주기의 절반 기대값). 최대 30초. GxP EDMS 결재 워크플로에서 수용 가능한 수준으로 QA와 합의.

### 채널 추상화 (future transport 대비)

```java
interface NotificationChannel {
    void deliver(NotificationOutbox item) throws ChannelDeliveryException;
    String channelName();
}
```

구현체: `InAppChannel` / `LogEmailChannel` / `SmtpEmailChannel`. SSE나 WebSocket으로 전환 시 `SseChannel` 구현체 추가만으로 완료 — OutboxDispatcher 변경 불필요.

### deprecated 보존

`GET /api/v1/notifications/stream` endpoint는 OpenAPI에 `deprecated: true` 표기 후 보존. 클라이언트가 없으므로 실제 호출 없음. M10+ 재평가 시 제거.

## 대안 검토

| 대안 | 거부 사유 |
|------|---------|
| SSE 유지 | L7 idle timeout 미검증, `maximumSessions(1)` 충돌 위험, 인프라 변경 비용 |
| WebSocket | SSE보다 복잡. 동일한 L7/세션 문제 존재 |
| 60초 폴링 | UX 저하 (최대 60초 지연). 30초로 충분한 DB 부하 |

## M10+ 재평가 조건

다음 조건이 모두 충족 시 SSE/WebSocket 재전환 검토:
1. L7 proxy idle timeout ≥ 5분 확인 또는 bypass 경로 확보
2. `maximumSessions(1)` + SSE 통합 테스트 통과
3. 사용자 규모 100명+ 성장으로 폴링 DB 부하가 PQ 기준 초과

## 결과

- DS §5.6 명세: work_queue + notification polling 통합 명세로 갱신
- `GET /notifications/stream`: OpenAPI `deprecated: true` + 보존
- `application.yml`: polling interval 설정 없음 (클라이언트 하드코딩 30s)
