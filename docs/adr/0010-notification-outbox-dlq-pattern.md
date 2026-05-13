# ADR 0010 — 알림 전송: Outbox + DLQ 패턴

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT |

## 맥락

알림을 신뢰성 있게 전송하려면 두 가지 문제를 해결해야 한다:

1. **트랜잭션 경계**: 결재 제출과 알림 발송이 같은 트랜잭션에 묶이면, 이메일 전송 실패 시 결재 자체가 롤백된다. 반대로 결재 커밋 후 알림 발송이 실패하면 무음 소실(silent loss)된다.

2. **채널 일시 장애**: SMTP 서버가 5분간 다운되면 그 사이 알림이 유실된다.

초기 설계(v1)의 단점:
- `@TransactionalEventListener(AFTER_COMMIT)` 안에서 바로 `ch.deliver()` 호출 → SMTP 성공 + DB 롤백 시 이메일 중복 발송, SMTP 실패 시 알림 소실.

## 결정

**Outbox 패턴 + DLQ + OutboxDispatcher.**

### 1단계: 이벤트 → Outbox INSERT 전용

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = REQUIRES_NEW)
public void onWorkQueueEvent(WorkQueueEvent e) {
    // outbox에 INSERT만 수행 (채널 호출 없음)
    outboxRepository.save(NotificationOutbox.pending(e));
}
```

`AFTER_COMMIT`은 원 트랜잭션 커밋 후 실행되므로 결재 롤백 걱정 없음. INSERT가 유일한 부수 작업이라 실패해도 재시도 가능.

### 2단계: OutboxDispatcher → 채널 전송

```java
@Scheduled(fixedDelay = 10_000)
@DistributedLock("outbox-dispatcher")  // ShedLock
public void dispatch() {
    List<NotificationOutbox> batch = outboxRepo.findByStatePending(100);
    for (var item : batch) {
        dispatchOne(item);  // REQUIRES_NEW 별도 트랜잭션 (E2)
    }
}

@Transactional(propagation = REQUIRES_NEW)  // E2: 행 단위 트랜잭션
private void dispatchOne(NotificationOutbox item) {
    item.markSending();
    outboxRepo.save(item);
    try {
        for (NotificationChannel ch : channels) {
            ch.deliver(item);
        }
        item.markSuccess();
    } catch (ChannelDeliveryException ex) {
        item.incrementRetry();
        if (item.getRetryCount() >= 3) {
            dlqRepository.save(NotificationDeadLetter.from(item));
            item.markFailed();
            auditService.log(NOTIFICATION_DELIVERY_FAILED, ...);
        }
    }
    outboxRepo.save(item);
}
```

**`REQUIRES_NEW`가 필요한 이유 (E2)**: 100건 배치를 단일 트랜잭션으로 묶으면 SMTP 성공 + DB 롤백 = 이메일 중복 발송. 행 단위 트랜잭션으로 분리해야 채널 성공/실패가 독립적으로 커밋된다.

### 재시도 정책

| 재시도 | 지연 |
|--------|------|
| 1회 | 1분 |
| 2회 | 5분 |
| 3회 | 30분 |
| 3회 초과 | DLQ 이동 + `NOTIFICATION_DELIVERY_FAILED` audit |

지수 백오프는 `retry_count`와 `next_retry_at` 컬럼으로 관리.

### SENDING stuck 복구 (E11)

크래시 발생 시 `notification_outbox.state = 'SENDING'` 행이 남을 수 있다. 재시작 후 `ApplicationRunner`가 실행되어 `next_retry_at < NOW() - INTERVAL '10 minutes'` 조건의 SENDING 행을 FAILED로 복구한다.

```java
@Component
public class OutboxStuckRecoverer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        int recovered = outboxRepo.resetStuckSending(Duration.ofMinutes(10));
        if (recovered > 0) log.warn("Recovered {} stuck SENDING rows", recovered);
    }
}
```

### payload_json 크기 제한 (E4)

`notification_outbox.payload_json`에는 인라인 이메일 HTML을 저장하지 않는다. **template key + params만 저장**한다.

```json
{"templateKey": "APPROVAL_REQUESTED", "params": {"docTitle": "...", "assigneeName": "..."}}
```

DB에 `CHECK (octet_length(payload_json::text) < 65536)` 제약 추가. 실제 이메일 렌더링은 `SmtpEmailChannel`이 발송 시점에 수행.

### DLQ 모니터링

`notification_dlq` 행은 관리자 화면 또는 `/api/v1/admin/notification-dlq` (M9 이후)로 조회. 현재는 `NOTIFICATION_DELIVERY_FAILED` audit_logs로 추적.

## 대안 검토

| 대안 | 거부 사유 |
|------|---------|
| 단일 단계 (이벤트 핸들러 → 채널 직접 호출) | SMTP 성공 + DB 롤백 = 중복 발송; 채널 실패 = 무음 소실 |
| Kafka/RabbitMQ | 폐쇄망 인프라 미비, 운영 복잡도 과도 |
| Spring `@Async` + 재시도 | 프로세스 재시작 시 큐 소실, 영속성 없음 |

## 결과

- `notification_outbox`: V26 migration
- `notification_dlq`: V26 migration
- `OutboxDispatcher`: ShedLock leader election + 10s 폴링 + 행 단위 `REQUIRES_NEW`
- `ApplicationRunner` stuck recovery
- `notification_outbox.payload_json`: template key + params only, CHECK < 65536
