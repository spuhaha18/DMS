# SOP-DR-001
# EDMS 재해 복구 절차 (Disaster Recovery SOP)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | SOP-DR-001 |
| 버전 | 1.0 |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 승인 전 |
| 분류 | GxP SOP — 컴퓨터화 시스템 인프라 |
| 작성자 | TBD |
| 검토자 | TBD (IT 인프라 담당) |
| 승인자 | TBD (QA Manager) |
| 다음 정기검토일 | 승인일 + 2년 |

---

## 1. 목적

본 SOP는 EDMS 운영 사이트에서 전체 시스템 장애(화재, 서버실 수재, 전원 장애, 사이버 공격 등)가 발생하였을 때 DR(Disaster Recovery) 사이트로 서비스를 전환하고 복구하는 절차를 정의한다.

**목표**: RTO(Recovery Time Objective) 4시간, RPO(Recovery Point Objective) 1시간.

---

## 2. 범위

| 항목 | 내용 |
|---|---|
| 적용 대상 | EDMS 전체 — App 서버, PostgreSQL DB, MinIO 오브젝트 스토리지 |
| DR 사이트 위치 | [DR 사이트 물리적 위치 — 별도 건물 또는 별도 데이터센터] |
| DR 트리거 조건 | 운영 사이트 서비스 완전 불가 상태가 2시간 이상 지속 예상 시 |

---

## 3. 용어 정의

| 용어 | 정의 |
|---|---|
| DR Failover | 운영 사이트 장애 시 DR 사이트로 서비스를 전환하는 절차 |
| Failback | 운영 사이트 복구 후 DR 사이트에서 운영 사이트로 서비스를 재전환하는 절차 |
| RTO | 장애 발생 시점부터 서비스 재개까지의 목표 시간 (4시간) |
| RPO | 복구 후 허용 가능한 최대 데이터 손실 기간 (1시간) |
| Logical Replication | PostgreSQL Primary → DR Replica 실시간 DB 복제 방식 |
| Site Replication | MinIO Primary → DR MinIO 실시간 버킷 복제 방식 |

---

## 4. 책임

| 역할 | DR 시 책임 |
|---|---|
| IT/인프라 담당 | DR 선언 검토, Failover 수행, 기술적 복구 |
| IT 책임자 | DR 선언 최종 결정 |
| QA | DR 중 데이터 무결성 확인, 복구 후 검증 |
| QA Manager | DR 사후 영향 평가 승인 |
| 시스템 관리자(Admin) | 사용자 접근 경로 변경 공지 |

---

## 5. DR 인프라 구성

### 5.1 DB 복제 (PostgreSQL Logical Replication)

```
운영 사이트 (Primary)          DR 사이트 (Replica)
PostgreSQL 16 Primary   →   PostgreSQL 16 Standby
  (edms_prod)                (edms_dr)
         │
         └── Logical Replication (실시간, 최대 지연 RPO 1시간 목표)
```

**평상시 복제 지연 모니터링**:
```sql
-- Primary에서 실행
SELECT client_addr, state, sent_lsn, write_lsn, replay_lsn,
       (sent_lsn - replay_lsn) AS replication_lag
FROM pg_stat_replication;
```
Prometheus가 `replication_lag > 1시간` 시 알람 발송.

### 5.2 MinIO Site Replication

MinIO Primary ↔ DR 간 Site Replication 실시간 동기화.  
`edms-audit-anchors` 버킷: COMPLIANCE Object Lock — DR에도 동일한 보존 정책 적용.

### 5.3 DR 사이트 컴포넌트

| 컴포넌트 | DR 사이트 상태 |
|---|---|
| App 서버 (Spring Boot) | 평상시 미실행 (Standby) — Failover 시 기동 |
| Nginx | 평상시 미실행 (Standby) — Failover 시 기동 |
| PostgreSQL | 실시간 Standby Replica (읽기 전용) |
| MinIO | 실시간 Site Replication |

---

## 6. DR 발동 기준

| 상황 | 조치 |
|---|---|
| 운영 사이트 서비스 불가 예상 시간 < 2시간 | 일반 장애 대응 (SOP-BACKUP-001 적용), DR 미발동 |
| 운영 사이트 서비스 불가 예상 시간 ≥ 2시간 | IT 책임자 판단 후 DR 발동 검토 |
| 운영 사이트 물리 손상 (화재, 수재) | 즉시 DR 발동 |
| 복구 불가 사이버 공격 (랜섬웨어 등) | 즉시 DR 발동 + 보안 대응 병행 |

---

## 7. DR Failover 절차

### 7.1 DR 발동 선언

1. IT 담당자가 운영 사이트 장애 상황을 IT 책임자에게 보고한다.
2. IT 책임자가 DR 발동을 결정하고 QA에게 통보한다.
3. DR 발동 시각을 기록한다.
4. 사용자에게 서비스 중단 및 DR 전환 공지를 발송한다 (이메일 + 사내 메신저).

### 7.2 DB Failover

1. DR 사이트 PostgreSQL Standby를 Primary로 승격(Promote)한다.
   ```bash
   # DR 서버에서 실행
   pg_ctl promote -D /var/lib/pgsql/16/data/
   ```
2. 승격 후 DB 상태를 확인한다.
   ```sql
   SELECT pg_is_in_recovery();  -- false여야 함 (Primary로 승격됨)
   ```
3. 마지막 WAL 수신 시각을 기록하여 RPO를 계산한다.
   ```sql
   SELECT now() - pg_last_xact_replay_timestamp() AS replication_lag;
   ```

### 7.3 MinIO Failover

MinIO Site Replication에 의해 DR 버킷은 이미 최신 상태이다.

1. DR MinIO에서 버킷 접근 가능 여부를 확인한다.
   ```bash
   mc ls dr-alias/edms-documents-original | head -5
   ```
2. 운영 MinIO 연결이 완전히 끊어졌음을 확인한다.

### 7.4 App 서버 기동

1. DR 사이트 App 서버에서 DB 연결 설정을 DR DB로 변경한다.
   ```yaml
   # /etc/edms/application.yml
   spring.datasource.url: jdbc:postgresql://dr-db-server:5432/edms_prod
   ```
2. Spring Boot 애플리케이션을 기동한다.
   ```bash
   systemctl start edms-app
   ```
3. 헬스체크 확인:
   ```bash
   curl -k https://dr-edms.internal/api/v1/actuator/health
   ```

### 7.5 Nginx 기동 및 DNS 전환

1. DR 사이트 Nginx를 기동한다.
   ```bash
   systemctl start nginx
   ```
2. DNS를 DR 사이트 IP로 전환한다 (IT 책임자 승인 후 DNS 담당자 수행).
   - `edms.internal` → DR 사이트 App 서버 IP
3. DNS 전파 완료 후 브라우저에서 로그인 테스트.

### 7.6 DR 전환 후 검증 (QA 수행)

QA는 아래 항목을 확인하고 **DR 전환 검증 기록서(부록 A)** 에 기록한다.

| 확인 항목 | 방법 | 결과 |
|---|---|---|
| 로그인 가능 여부 | 테스트 계정으로 로그인 | ☐ Pass ☐ Fail |
| 문서 목록 조회 | 시행중 문서 1건 조회 | ☐ Pass ☐ Fail |
| 감사로그 기록 | 로그인 후 audit_logs 기록 확인 | ☐ Pass ☐ Fail |
| 최근 문서 데이터 존재 | 최근 1시간 내 생성 문서 존재 여부 | ☐ Pass ☐ Fail |
| MinIO 파일 접근 | 문서 PDF 열람 가능 여부 | ☐ Pass ☐ Fail |

**DR 전환 완료 선언**: QA와 IT 책임자 공동 확인 서명 후 사용자에게 서비스 재개 공지.

---

## 8. DR 운영 중 주의사항

DR 사이트 운영 중:
- **모든 감사로그·서명은 DR DB에 정상 기록됨** — GxP 기록 연속성 유지.
- 일별 WORM 앵커링 스케줄러가 DR 서버에서도 동작하도록 확인한다.
- 운영 사이트가 복구되는 즉시 Failback 절차를 준비한다.

---

## 9. Failback 절차 (운영 사이트 복구 후)

### 9.1 Failback 전제조건

- 운영 사이트 서버 완전 복구 확인
- IT 책임자 Failback 결정 및 QA 통보
- Failback 작업 공지 (최소 1시간 전 사용자 공지)

### 9.2 Failback 절차

1. **DR → 운영 DB 동기화**:
   - DR DB에서 운영 사이트 복구 후의 증분 데이터를 백업한다.
   - 운영 DB를 최신 상태로 복원한다 (DR에서 생성된 데이터 포함).
   - 운영 DB를 새 Primary로 설정한다.
   
2. **MinIO 역방향 동기화**:
   - DR 기간 중 업로드된 파일이 운영 MinIO에 동기화되었는지 확인한다.
   - 필요 시 수동 동기화: `mc mirror dr-alias/edms-documents-original primary-alias/edms-documents-original`

3. **App 서버 전환**:
   - 운영 사이트 App 서버 DB 연결을 운영 DB로 원복한다.
   - 운영 사이트 App 서버 기동 및 헬스체크.
   - DR 사이트 App 서버 중단.

4. **DNS 원복**:
   - `edms.internal` → 운영 사이트 IP로 원복.

5. **Failback 완료 검증** (§7.6과 동일 항목).

6. **Failback 완료 공지** 및 DR 전환·운영·복구 이력 감사로그 수동 기록 (QA 수행).

---

## 10. DR 훈련

**주기**: 반기 1회 (SOP-BACKUP-001 §7과 병행 또는 독립 수행)  
**방법**: DR 서버에서 Failover 절차 (§7.2~7.6) 전체 수행  
**결과 기록**: 부록 A (DR 전환 검증 기록서) 작성 + QA Manager 서명

---

## 11. 양식

### 부록 A — DR 전환 검증 기록서

```
DR 발동 일시:
DR 발동 사유:
IT 책임자 서명: ______________ 날짜: ______

[Failover 수행 기록]
DB Promote 완료 시각:
App 서버 기동 완료 시각:
DNS 전환 완료 시각:
DR 전환 완료 시각 (서비스 재개):

총 RTO (발동 선언 → 서비스 재개): ____시간 ____분

RPO 산출:
  - DR DB 마지막 WAL 수신 시각:
  - DR 발동 시각:
  - RPO (데이터 손실 구간): ____분

[DR 전환 후 검증]
로그인 가능: ☐ Pass ☐ Fail
문서 조회: ☐ Pass ☐ Fail
감사로그 기록: ☐ Pass ☐ Fail
MinIO 파일 접근: ☐ Pass ☐ Fail

[Failback 완료 기록] (해당 시)
Failback 완료 시각:
데이터 정합성 확인:

IT 담당자 서명: ______________ 날짜: ______
QA 서명: ____________________ 날짜: ______
QA Manager 서명: _____________ 날짜: ______
```

---

## 12. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-DS-001 | Design Specification §3.1, §10.1, §10.3 |
| SOP-BACKUP-001 | Backup & Restore SOP |
| SOP-CHANGE-001 | Change Control SOP |
| EU GMP Annex 11 | §12 (Disaster Recovery) |

---

## 13. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 1.0 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 승인 전까지 운영 절차로 사용할 수 없다.*
