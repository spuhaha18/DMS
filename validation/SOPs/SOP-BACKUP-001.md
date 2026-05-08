# SOP-BACKUP-001
# EDMS 백업 및 복구 절차 (Backup & Restore SOP)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | SOP-BACKUP-001 |
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

본 SOP는 EDMS의 데이터(PostgreSQL DB, MinIO 오브젝트 스토리지, 애플리케이션 설정)를 정기적으로 백업하고, 장애 발생 시 데이터를 복구하는 절차를 정의한다.

GxP 기록의 영구 보존 및 시스템 가용성 유지를 목적으로 하며, 복구 검증을 통해 백업 유효성을 주기적으로 확인한다.

---

## 2. 범위

| 백업 대상 | 내용 |
|---|---|
| PostgreSQL 16 (Primary) | 메타데이터, 감사로그, 서명 매니페스트, 사용자·권한 정보 |
| MinIO (Primary) | 원본 문서 파일, PDF 렌디션, 감사 WORM 앵커 파일 |
| 애플리케이션 설정 | application.yml, Nginx 설정, 환경 변수 |
| Flyway 마이그레이션 스크립트 | DB 스키마 히스토리 |

---

## 3. 용어 정의

| 용어 | 정의 |
|---|---|
| Full Backup | DB 전체 데이터를 하나의 백업 파일로 저장 (pg_basebackup) |
| WAL (Write-Ahead Log) | PostgreSQL의 변경 사전 기록 파일 — 증분 복구에 사용 |
| PITR (Point-in-Time Recovery) | WAL 아카이브를 이용하여 특정 시점으로 DB를 복원하는 기법 |
| Object Lock (WORM) | MinIO에서 설정한 삭제·수정 불가 보존 정책 |
| RTO (Recovery Time Objective) | 목표 복구 시간 — 장애 발생 후 서비스 재개까지의 최대 허용 시간 |
| RPO (Recovery Point Objective) | 목표 복구 시점 — 복구 후 허용 가능한 최대 데이터 손실 기간 |

**RTO/RPO 목표**: RTO 4시간, RPO 1시간

---

## 4. 책임

| 역할 | 책임 |
|---|---|
| IT/인프라 담당 | 백업 스케줄 설정·유지, 백업 성공 여부 모니터링, 복구 수행 |
| QA | 반기 복구 훈련 참관 및 결과 검토·서명 |
| QA Manager | 복구 훈련 결과 최종 승인 |
| 시스템 관리자(Admin) | 백업 실패 알림 수신 시 IT에 즉시 통보 |

---

## 5. 백업 절차

### 5.1 PostgreSQL Full Backup (매일 자동)

**스케줄**: 매일 00:00 KST (cron)  
**방법**: `pg_basebackup`  
**저장 경로**: `/backup/postgres/daily/YYYYMMDD/`  
**보관 주기**: 최근 30일 (30일 초과분 자동 삭제)

**cron 설정 예시**:
```bash
0 0 * * * /usr/local/bin/edms-pg-backup.sh >> /var/log/edms-backup.log 2>&1
```

**백업 스크립트 주요 동작** (`edms-pg-backup.sh`):
```bash
BACKUP_DIR="/backup/postgres/daily/$(date +%Y%m%d)"
mkdir -p "$BACKUP_DIR"

# Full backup 실행
pg_basebackup -h localhost -U backup_user -D "$BACKUP_DIR" -Ft -z -P

# SHA-256 해시 생성 (무결성 검증용)
sha256sum "$BACKUP_DIR"/base.tar.gz > "$BACKUP_DIR"/base.tar.gz.sha256

# 30일 초과분 삭제
find /backup/postgres/daily/ -maxdepth 1 -type d -mtime +30 -exec rm -rf {} \;

# 백업 성공 로그
echo "$(date -Iseconds) [INFO] PostgreSQL backup completed: $BACKUP_DIR"
```

**백업 실패 시**: Prometheus 알람 → 사내 메신저 알림 → IT 담당자 즉시 확인

---

### 5.2 WAL 아카이빙 (연속)

**방법**: PostgreSQL `archive_mode = on`, `archive_command` 설정  
**저장 경로**: `/backup/postgres/wal/`  
**역할**: Full Backup 사이의 변경사항을 PITR로 복구하기 위해 사용

**postgresql.conf 설정**:
```
archive_mode = on
archive_command = 'cp %p /backup/postgres/wal/%f'
wal_level = replica
```

**WAL 보관**: 최근 7일 분량 유지 (7일 초과분은 Full Backup 검증 후 삭제)

---

### 5.3 MinIO Site Replication (실시간 자동)

MinIO Primary 버킷은 DR 사이트의 MinIO와 실시간 Site Replication이 설정되어 있다 (DS §10.2).

| 버킷 | Primary | DR 복제본 | Object Lock |
|---|---|---|---|
| edms-documents-original | 운영 서버 | DR 서버 | GOVERNANCE 10년 |
| edms-documents-rendition | 운영 서버 | DR 서버 | GOVERNANCE 10년 |
| edms-audit-anchors | 운영 서버 | DR 서버 | COMPLIANCE 10년 |

**모니터링**: `mc admin replicate info <alias>` — Prometheus가 복제 지연을 감시하며, 1시간 초과 시 알람 발송.

---

### 5.4 애플리케이션 설정 백업 (주 1회 수동)

**스케줄**: 매주 월요일 업무 시작 전  
**대상 파일**:
- `/etc/edms/application.yml` (Spring Boot 설정)
- `/etc/nginx/conf.d/edms.conf` (Nginx 설정)
- `/etc/edms/.env` (환경 변수 — 비밀번호 포함, 암호화 보관)

**절차**:
1. 설정 파일을 `/backup/config/YYYYMMDD/`에 복사
2. 암호화 압축: `gpg --symmetric --cipher-algo AES256 -o config.tar.gz.gpg config.tar.gz`
3. 암호화 파일을 오프라인 매체(사내 NAS 또는 별도 서버)에 저장

---

### 5.5 백업 성공 여부 일일 확인

IT 담당자는 매일 업무 시작 후 30분 이내에 전날 백업 성공 여부를 확인한다.

**확인 방법**:
```bash
# 백업 로그 마지막 10줄 확인
tail -20 /var/log/edms-backup.log

# 어제 백업 디렉터리 존재 및 파일 크기 확인
ls -lh /backup/postgres/daily/$(date -d yesterday +%Y%m%d)/
```

**확인 결과 기록**: 백업 모니터링 대장(부록 A)에 일별 기록.

---

## 6. 복구 절차

> ⚠️ **주의**: 운영 DB 복구는 되돌릴 수 없다. 복구 전 반드시 현재 상태의 추가 백업을 수행하고, QA에게 통보한다.

### 6.1 복구 시나리오 분류

| 시나리오 | 복구 방법 | 절차 |
|---|---|---|
| DB 논리 오류 (데이터 잘못 입력 등) | PITR (특정 시점 복원) | §6.2 |
| DB 서버 물리 장애 (HDD 고장 등) | Full Backup + WAL 적용 | §6.3 |
| MinIO 파일 단순 손상 | DR 복제본에서 복원 | §6.4 |
| 전체 사이트 장애 | DR Failover (SOP-DR-001 적용) | SOP-DR-001 참조 |

---

### 6.2 PITR 복구 (특정 시점 복원)

**적용 상황**: 논리 오류 발생 시점 직전으로 DB를 복원해야 하는 경우

1. 복구 목표 시점(Target Time)을 QA와 협의하여 결정한다.
2. QA에게 복구 시작 통보 및 승인을 받는다.
3. 현재 DB 상태를 임시 백업한다.
   ```bash
   pg_basebackup -h localhost -U backup_user -D /backup/postgres/emergency/$(date +%Y%m%d_%H%M%S) -Ft -z
   ```
4. PostgreSQL 서비스를 중단한다.
   ```bash
   systemctl stop postgresql-16
   ```
5. 가장 최근 Full Backup을 데이터 디렉터리에 복원한다.
   ```bash
   tar -xzf /backup/postgres/daily/YYYYMMDD/base.tar.gz -C /var/lib/pgsql/16/data/
   ```
6. `recovery.conf` (또는 `postgresql.conf`에 recovery 설정) 작성:
   ```
   restore_command = 'cp /backup/postgres/wal/%f %p'
   recovery_target_time = '2026-05-08 09:30:00+09'
   recovery_target_action = 'promote'
   ```
7. PostgreSQL 서비스를 시작한다.
8. 복구 완료 후 `recovery_target_time` 이후 데이터 손실 범위를 QA에게 보고한다.
9. QA가 손실 데이터 범위를 확인하고 감사로그에 복구 이벤트를 기록한다.

---

### 6.3 Full Backup + WAL 복구 (물리 장애)

1. 신규 또는 복구된 DB 서버에서 PostgreSQL을 설치하고 빈 데이터 디렉터리를 준비한다.
2. 가장 최근 Full Backup을 복원한다 (§6.2 4~5단계 참고).
3. WAL 아카이브를 `/backup/postgres/wal/`에서 접근 가능한 위치에 복사한다.
4. `restore_command`를 설정하고 서비스를 시작한다.
5. 복구 완료 후 핵심 테이블 레코드 수를 마지막 알려진 상태와 비교한다.
   ```sql
   SELECT COUNT(*) FROM documents;
   SELECT COUNT(*) FROM audit_logs;
   SELECT COUNT(*) FROM signature_manifests;
   ```
6. QA에게 복구 결과 보고 및 감사로그 이벤트 기록.

---

### 6.4 MinIO 파일 복원 (단일 파일 또는 버킷)

```bash
# DR 복제본에서 특정 파일 복원
mc cp dr-alias/edms-documents-original/<object-key> \
       primary-alias/edms-documents-original/<object-key>

# 복원 후 SHA-256 무결성 검증
mc stat primary-alias/edms-documents-original/<object-key>
# DB의 document_files.sha256_hash 값과 비교
```

---

## 7. 복구 검증 (반기 복구 훈련)

### 7.1 목적

실제 장애 상황에서 복구가 가능하고 RTO 4시간 이내임을 정기적으로 확인한다.

### 7.2 스케줄

**주기**: 반기 1회 (3월, 9월)  
**환경**: DR 서버 (운영 서버와 분리된 환경)  
**참관**: QA 반드시 참관

### 7.3 복구 훈련 절차

1. 훈련 시작 시각 기록
2. DR 서버에서 DB 완전 초기화 (데이터 디렉터리 삭제)
3. 최신 Full Backup 복원 + WAL 적용
4. 핵심 테이블 레코드 수 비교 (운영 Primary 기준):
   ```sql
   -- DR 복원 DB에서 실행
   SELECT 'documents' AS tbl, COUNT(*) FROM documents
   UNION ALL SELECT 'audit_logs', COUNT(*) FROM audit_logs
   UNION ALL SELECT 'signature_manifests', COUNT(*) FROM signature_manifests;
   ```
5. MinIO DR 버킷에서 임의 파일 1건 다운로드 후 SHA-256 검증
6. 훈련 완료 시각 기록

### 7.4 합격 기준

| 항목 | 기준 |
|---|---|
| 복구 소요 시간 | < 4시간 (RTO 목표) |
| documents 레코드 수 | Primary와 일치 |
| audit_logs 레코드 수 | Primary와 일치 |
| MinIO 파일 무결성 | SHA-256 일치 |

### 7.5 훈련 결과 기록

복구 훈련 결과를 **복구 훈련 기록서(부록 B)** 에 기록하고 QA Manager가 서명한다. 결과는 PQ Report 및 Periodic Review에 활용된다.

---

## 8. 양식

### 부록 A — 백업 모니터링 대장

| 날짜 | 수행자 | Full Backup 결과 | WAL 아카이빙 | MinIO 복제 상태 | 비고 |
|---|---|---|---|---|---|
| YYYY-MM-DD | | ☐ 성공 ☐ 실패 | ☐ 정상 ☐ 이상 | ☐ 정상 ☐ 이상 | |

### 부록 B — 복구 훈련 기록서

```
훈련 일자:
수행자:
QA 참관자:

훈련 시작 시각:
훈련 완료 시각:
총 소요 시간:

[복구 결과]
documents 건수 (Primary / DR):
audit_logs 건수 (Primary / DR):
MinIO 파일 SHA-256 검증: ☐ 일치 ☐ 불일치

[합격 판정]
RTO 충족 여부: ☐ Pass ☐ Fail
데이터 정합성: ☐ Pass ☐ Fail
종합 판정:     ☐ Pass ☐ Fail

[이탈 사항]

수행자 서명: ______________ 날짜: ______
QA 서명: __________________ 날짜: ______
QA Manager 서명: ___________ 날짜: ______
```

---

## 9. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-DS-001 | Design Specification §10.3 |
| SOP-DR-001 | Disaster Recovery SOP |
| SOP-CHANGE-001 | Change Control SOP |
| EU GMP Annex 11 | §7.1 (Data Storage), §9 (Audit Trails) |

---

## 10. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 1.0 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 승인 전까지 운영 절차로 사용할 수 없다.*
