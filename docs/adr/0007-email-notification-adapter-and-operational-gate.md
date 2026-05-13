# ADR 0007 — 이메일 알림 어댑터 + 운영 활성화 게이트 분리

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA |

## 맥락

URS-NTFY-001은 결재 요청 시 이메일 알림 발송을 요구한다. 그러나 M8 개발 시점에 사내 SMTP relay(`smtp.lab.internal:25`)가 미준비 상태이고 Vault `SMTP_PASSWORD` 주입 일정도 미확정이다.

기존 접근 방식(v1 계획):
- 이메일 구현 자체를 M8 이후로 연기 → URS-NTFY-001 부분 미충족 상태로 배포.

문제점:
- URS 추적성 공백이 IQ/OQ 감사 시 질의 대상이 된다.
- 코드를 나중에 추가하면 회귀 위험 발생.

## 결정

**코드 완성과 운영 활성화를 분리한다.**

### 코드 전략

`SmtpEmailChannel` 구현을 M8 PR4에서 완성하고 GreenMail Testcontainer 통합 테스트로 검증한다.

```java
@Component
@ConditionalOnProperty("notification.email.enabled", havingValue = "true")
class SmtpEmailChannel implements NotificationChannel {
    private final JavaMailSender mailSender;
    // ...
}
```

`notification.email.enabled=false`(기본값)이면 Spring이 이 Bean을 생성하지 않는다. OutboxDispatcher는 등록된 채널만 순회하므로 SMTP 호출 없음.

`LogEmailChannel`은 `!smtp` 프로파일(또는 `notification.email.enabled=false`)에서 활성화되어 "이메일 전송됨(로그)" 로그를 기록한다. OQ 시나리오 OQ-NTFY-111에서 검증.

### 운영 활성화 조건 (3-way gate)

다음 3가지가 모두 충족된 후에만 `notification.email.enabled=true`로 전환한다:

1. IT팀 SMTP relay 가동 확인 (`telnet smtp.lab.internal 25` 성공)
2. Vault `SMTP_PASSWORD` 주입 완료 및 테스트 발송 성공
3. dev/stage 환경 GreenMail → 실제 SMTP 전환 IT 통과

전환 시 `application.yml` 수정 + 재배포. **코드 변경 없음.**

### 기존 stub 처리

`EmailNotificationService.java` (인터페이스) + `LogEmailNotificationService.java` (`@Profile("!smtp")`)는 즉시 삭제하지 않는다. `NotificationChannel` 패턴으로 점진 마이그레이션. M8 PR4에서 `LogEmailChannel`이 동일 역할을 수행하면 기존 stub은 M9에서 제거.

## URS 추적성

| URS ID | 요건 | 충족 방법 |
|--------|------|---------|
| URS-NTFY-001 | 결재 요청 시 이메일 알림 | 설계 완료 (`SmtpEmailChannel`). 운영 활성화는 SMTP relay 준비 후 |

OQ-NTFY-112: GreenMail 수신 확인 — M8 PR4에서 통합 테스트로 검증. URS-NTFY-001 **설계 충족** 표기.

## 대안 검토

| 대안 | 거부 사유 |
|------|---------|
| M8에서 이메일 구현 생략 | URS 추적성 공백, M9 추가 시 회귀 위험 |
| 외부 이메일 서비스(SaaS) | 폐쇄망 환경 — 인터넷 egress 차단 |
| 직접 SMTP 소켓 | Spring Mail 재발명, 유지보수 불가 |

## 결과

- `backend/build.gradle`: `spring-boot-starter-mail` + `com.icegreen:greenmail-spring`(test) 추가
- `application.yml`: `notification.email.enabled: false` (기본), `spring.mail.host/port` (profile별)
- `SmtpEmailChannel`: M8 PR4에서 완성 + GreenMail IT 통과
- URS-NTFY-001: 설계 완료, 운영 활성화 보류로 Traceability Matrix 표기
