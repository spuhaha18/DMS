# SOP-AUDIT-TRAIL-001
# EDMS 감사추적 검토 절차 (Audit Trail Review SOP)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | SOP-AUDIT-TRAIL-001 |
| 버전 | 1.0 |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 승인 전 |
| 분류 | GxP SOP — 컴퓨터화 시스템 감사추적 |
| 작성자 | TBD |
| 검토자 | TBD (QA) |
| 승인자 | TBD (QA Manager) |
| 다음 정기검토일 | 승인일 + 2년 |

---

## 1. 목적

본 SOP는 EDMS 감사추적(audit_logs, signature_manifests, audit_checkpoints)의 정기 검토 절차를 정의한다. 21 CFR Part 11 §11.10(e)는 감사추적이 안전하게 유지되고 검토 가능해야 함을 요구한다. 본 절차는 무결성 침해·이상 패턴을 조기에 발견하고 조치함으로써 전자기록의 법적 효력을 유지한다.

---

## 2. 범위

본 SOP는 다음 3개 감사 데이터 소스에 적용된다:

| 대상 | 위치 | 검토 주기 |
|---|---|---|
| `audit_logs` 테이블 | PostgreSQL (INSERT-only, audit_role 전용) | 일별(자동) / 주별(수동) / 월별(종합) |
| `signature_manifests` 테이블 | PostgreSQL (INSERT-only, audit_role 전용) | 주별(수동) / 월별(종합) |
| `audit_checkpoints` + MinIO WORM 앵커 | PostgreSQL + MinIO COMPLIANCE 버킷 | 월별(종합) |

---

## 3. 용어 정의

| 용어 | 정의 |
|---|---|
| 해시체인 | 각 audit_log/signature_manifest 레코드에 `prev_hash + payload → this_hash` 방식으로 연결된 SHA-256 체인 |
| WORM 앵커 | 전날 audit_logs 전체의 Merkle root를 MinIO COMPLIANCE 버킷에 저장한 불변 앵커 파일 |
| 해시 단절 | `current.prev_hash ≠ previous.this_hash`인 레코드가 발견된 경우 |
| 이상 이벤트 | 비업무시간 로그인, 단기 과다 서명, Admin 권한 오용, 직접 DB 조작 시도 |

---

## 4. 책임

| 역할 | 책임 |
|---|---|
| 시스템 (자동) | 일별 해시체인 단절 감지 + WORM 앵커 생성 |
| 시스템 관리자 | 자동 알림 수신·대응, 주별 샘플 검토 실행 |
| QA | 월별 종합 검토 참여, 검토 기록서 공동 서명 |
| QA Manager | 이상 발견 시 보고 수신, 검토 기록서 최종 승인 |

---

## 5. 절차

### 5.1 일별 자동 검토 (시스템 자동)

매일 01:00 KST에 자동 실행되는 배치 작업이 다음을 수행한다:

**5.1.1 해시체인 단절 감지**

```sql
-- 전날 audit_logs에서 해시체인 연속성 확인
-- LAG() 사용: PostgreSQL sequence gap(id 불연속)에 의한 false positive 방지
WITH chain AS (
  SELECT id,
         server_ts,
         prev_hash,
         this_hash,
         LAG(this_hash) OVER (ORDER BY id) AS expected_prev
  FROM audit_logs
  WHERE server_ts >= NOW() - INTERVAL '2 days'
    AND server_ts < NOW() - INTERVAL '1 day'
)
SELECT id, prev_hash, expected_prev,
       CASE
         WHEN expected_prev IS NULL THEN 'FIRST_ROW'  -- 해당 기간 첫 행, 별도 genesis/이전앵커 검증 필요
         WHEN prev_hash = expected_prev THEN 'OK'
         ELSE 'BROKEN'
       END AS status
FROM chain
WHERE expected_prev IS NULL OR prev_hash != expected_prev;
```

> **참고**: `b.id = a.id - 1` JOIN 방식은 PostgreSQL sequence gap (트랜잭션 롤백, advisory lock 경쟁 등으로 발생하는 정상적 id 불연속) 시 false positive를 유발한다. `LAG(this_hash) OVER (ORDER BY id)`는 실제 삽입 순서를 기준으로 체인을 검증한다.

- 결과가 1건 이상이면 **즉시 알림**: Admin + QA에 이메일 + 시스템 알림 발송
- 알림 메시지: `[CRITICAL] 감사추적 해시 단절 감지 — audit_log.id={id}, 즉시 SOP-INCIDENT-001 발동`

**5.1.2 WORM 앵커 생성 확인**

- `audit_checkpoints` 테이블에서 어제 날짜 앵커가 생성되었는지 확인
- 미생성 시: 즉시 재실행 시도 → 실패 시 Admin + QA 알림 발송

**5.1.3 자동 알림 기준**

| 이상 유형 | 알림 대상 | 즉각 조치 |
|---|---|---|
| 해시 단절 1건 이상 | Admin, QA, QA Manager | SOP-INCIDENT-001 발동 (Critical) |
| WORM 앵커 미생성 | Admin | 앵커 재생성 시도 후 보고 |
| MinIO COMPLIANCE 버킷 접근 오류 | Admin | 즉시 원인 조사 |

---

### 5.2 주별 샘플 검토 (시스템 관리자 수행)

**주기**: 매주 월요일 또는 화요일 (업무일 기준)  
**소요 시간**: 약 30분  
**기록**: 주별 검토 기록서(부록 A) 작성 → EDMS에 저장

**5.2.1 무작위 샘플 50건 검토**

```sql
-- 지난 주 audit_logs에서 무작위 50건 추출
SELECT id, actor_user_id, action, entity_type, entity_id,
       server_ts, client_ip, prev_hash, this_hash
FROM audit_logs
WHERE server_ts >= NOW() - INTERVAL '7 days'
ORDER BY RANDOM()
LIMIT 50;
```

검토 항목:
- [ ] `actor_user_id` — 실존하는 활성 사용자인가? (시스템 자동 이벤트 제외)
- [ ] `action` — 허용된 action 코드인가?
- [ ] `client_ip` — 사내 IP 범위(10.10.x.x)인가?
- [ ] `server_ts` — 비업무 시간(22:00~06:00) 이벤트가 있는가? 있다면 정당한가?
- [ ] 해시체인: `SHA256(prev_hash ∥ actor_user_id ∥ action ∥ entity_type:entity_id ∥ server_ts) == this_hash`

**5.2.2 서명 매니페스트 샘플 20건 검토**

```sql
SELECT id, signer_user_id, meaning, signed_at, client_ip, prev_hash, this_hash
FROM signature_manifests
WHERE signed_at >= NOW() - INTERVAL '7 days'
ORDER BY RANDOM()
LIMIT 20;
```

검토 항목:
- [ ] 서명 의미(meaning)가 해당 워크플로 단계에 적합한가?
- [ ] 서명 시각이 워크플로 인스턴스 시간 범위 내인가?
- [ ] 해시체인 연속성

**5.2.3 이상 발견 시**

- 이상 사항이 있으면 부록 A 양식에 상세 기록
- 심각도 판단: SOP-DEVIATION-001 §3 기준 적용
- Critical → SOP-INCIDENT-001 즉시 발동
- Major/Minor → SOP-DEVIATION-001 보고

---

### 5.3 월별 종합 검토 (시스템 관리자 + QA 공동)

**주기**: 매월 1~5일 사이 (전월 검토)  
**소요 시간**: 약 2시간  
**기록**: 월별 감사추적 검토 기록서(부록 B) 작성 → QA, 시스템 관리자 공동 서명 → EDMS에 저장

**5.3.1 WORM 앵커 무결성 확인**

```sql
-- 전월 audit_checkpoints 목록 확인
SELECT checkpoint_date, merkle_root, record_count, first_log_id, last_log_id, minio_key
FROM audit_checkpoints
WHERE checkpoint_date >= DATE_TRUNC('month', NOW() - INTERVAL '1 month')
  AND checkpoint_date < DATE_TRUNC('month', NOW())
ORDER BY checkpoint_date;
```

- 결과 행 수 = 전월 일수이어야 함
- 각 행의 `minio_key` 존재 확인:
  ```bash
  mc stat edms-audit-anchors/anchors/YYYY/MM/YYYYMMdd.json
  ```
- Merkle root 재계산 검증 (옵션, 의심 시):
  ```bash
  # API endpoint 사용
  POST /api/v1/audit-logs/checkpoints/verify
  { "from_date": "YYYY-MM-01", "to_date": "YYYY-MM-31" }
  ```

**5.3.2 이상 패턴 분석**

```sql
-- 비업무 시간(22:00~06:00) 이벤트 목록
SELECT actor_user_id, action, server_ts, client_ip
FROM audit_logs
WHERE server_ts >= DATE_TRUNC('month', NOW() - INTERVAL '1 month')
  AND EXTRACT(HOUR FROM server_ts AT TIME ZONE 'Asia/Seoul') NOT BETWEEN 6 AND 22
ORDER BY server_ts;
```

```sql
-- 1시간 내 10건 이상 서명한 사용자
SELECT signer_user_id, COUNT(*) AS sign_count,
       DATE_TRUNC('hour', signed_at) AS hour_slot
FROM signature_manifests
WHERE signed_at >= DATE_TRUNC('month', NOW() - INTERVAL '1 month')
GROUP BY signer_user_id, DATE_TRUNC('hour', signed_at)
HAVING COUNT(*) >= 10
ORDER BY sign_count DESC;
```

```sql
-- Admin 역할 사용자 행위 100% 검토
SELECT a.actor_user_id, a.action, a.entity_type, a.entity_id, a.server_ts
FROM audit_logs a
JOIN users u ON u.user_id = a.actor_user_id
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE r.role_code = 'ADMIN'
  AND a.server_ts >= DATE_TRUNC('month', NOW() - INTERVAL '1 month')
ORDER BY a.server_ts;
```

**5.3.3 검토 결과 분류 및 조치**

| 발견 유형 | 조치 |
|---|---|
| 이상 없음 | 부록 B에 "이상 없음" 기록 후 서명 |
| 비업무 시간 이벤트 (정당) | 해당 사용자 확인 (긴급 작업 등), 정당 사유 부록 B에 기록 |
| 비업무 시간 이벤트 (불분명) | SOP-INCIDENT-001 §5.1 Detection 절차 적용 |
| 과다 서명 | 해당 사용자 면담 → 의도적 서명 위조 의심 시 즉시 비활성화 |
| Admin 부적절 행위 | SOP-INCIDENT-001 발동 |

---

### 5.4 분기 추세 분석 (SOP-PERIODIC-001 연계)

SOP-PERIODIC-001 §5.2 시스템 정기검토에서 분기별 감사추적 추세를 분석한다:
- 분기별 총 이벤트 수 추이
- 비업무 시간 이벤트 수 추이
- 서명 오류(실패) 발생 수
- 이상 발견 건수 및 처리 결과

분기 추세 데이터는 시스템 정기검토 보고서에 포함된다.

---

## 6. 양식

### 부록 A — 주별 감사추적 샘플 검토 기록서

```
검토 기간: YYYY-MM-DD ~ YYYY-MM-DD
샘플 수: audit_logs ___건, signature_manifests ___건
검토자:
검토일:

[검토 결과]
☐ 이상 없음
☐ 이상 발견 (상세 아래 기재)

이상 사항:
  audit_log.id / signature_manifest.id:
  이상 내용:
  조치 (SOP-INCIDENT 또는 SOP-DEVIATION 번호):

서명: ______________ 날짜: ______
```

### 부록 B — 월별 감사추적 종합 검토 기록서

```
검토 대상 월: YYYY년 MM월
검토일: YYYY-MM-DD
시스템 관리자:
QA:

[WORM 앵커]
전월 일수: ___일   앵커 생성 수: ___건   누락: ___건
Merkle root 재검증: ☐ 실행 ☐ 미실행 (이상 없음 전제)

[이상 패턴]
비업무시간 이벤트: ___건 (정당: ___건, 미확인: ___건)
과다 서명(1h/10건): ___건
Admin 행위 총 건수: ___건   이상: ☐ 없음 ☐ 있음

[특이 사항]

[조치 내역]
SOP-INCIDENT / SOP-DEVIATION 번호:

시스템 관리자 서명: ______________ 날짜: ______
QA 서명: __________________________ 날짜: ______
```

---

## 7. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-DS-001 | §4.2 audit_logs DDL, §4.3 DB 역할, §8.2~8.3 해시체인·WORM |
| VAL-FS-001 | FS-AUD-001~007 |
| SOP-INCIDENT-001 | 보안사고 대응 절차 |
| SOP-DEVIATION-001 | 운영 일탈 관리 절차 |
| SOP-PERIODIC-001 | 정기검토 절차 (§5.2 시스템 정기검토) |
| SOP-USER-001 | §5.6 계정 이상 징후 대응 |
| 21 CFR Part 11 | §11.10(e) Audit Trail |
| EU GMP Annex 11 | §9 Audit Trails |

---

## 8. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 1.0 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 승인 전까지 운영 절차로 사용할 수 없다.*
