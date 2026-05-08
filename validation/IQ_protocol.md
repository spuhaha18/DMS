# Installation Qualification Protocol (IQ 프로토콜)
## Electronic Document Management System (EDMS)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-IQ-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토·승인 전 |
| 검증 단계 | IQ (Installation Qualification) |
| URS 참조 | VAL-URS-001 v0.3 |
| DS 참조 | VAL-DS-001 v0.1 |
| TM 참조 | VAL-TM-001 v0.1 |
| 작성자 | TBD |
| 검토자 | TBD |
| 승인자 | TBD (QA) |

---

## 목차

1. [목적 및 범위](#1-목적-및-범위)
2. [참조 문서](#2-참조-문서)
3. [IQ 수행 전제조건](#3-iq-수행-전제조건)
4. [IQ 체크리스트 — 인프라 하드웨어](#4-iq-체크리스트--인프라-하드웨어)
5. [IQ 체크리스트 — OS 및 기본 설정](#5-iq-체크리스트--os-및-기본-설정)
6. [IQ 체크리스트 — PostgreSQL](#6-iq-체크리스트--postgresql)
7. [IQ 체크리스트 — MinIO](#7-iq-체크리스트--minio)
8. [IQ 체크리스트 — LibreOffice](#8-iq-체크리스트--libreoffice)
9. [IQ 체크리스트 — Java / Spring Boot 애플리케이션](#9-iq-체크리스트--java--spring-boot-애플리케이션)
10. [IQ 체크리스트 — Nginx / TLS](#10-iq-체크리스트--nginx--tls)
11. [IQ 체크리스트 — 백업 설정](#11-iq-체크리스트--백업-설정)
12. [IQ 체크리스트 — 네트워크 및 보안](#12-iq-체크리스트--네트워크-및-보안)
13. [IQ 체크리스트 — DB 역할 및 권한 분리](#13-iq-체크리스트--db-역할-및-권한-분리)
14. [IQ 체크리스트 — NTP 동기화](#14-iq-체크리스트--ntp-동기화)
15. [IQ 요약 및 서명](#15-iq-요약-및-서명)
16. [이탈(Deviation) 기록](#16-이탈deviation-기록)
17. [변경 이력](#17-변경-이력)

---

## 1. 목적 및 범위

본 프로토콜은 EDMS 운영 환경이 DS(VAL-DS-001)에서 정의된 사양에 따라 올바르게 설치되었음을 검증한다. IQ는 소프트웨어가 기능하기 전에 물리적·논리적 설치 기준이 충족되었는지 확인하는 단계이다.

**IQ 범위**:
- 운영 서버 (App, DB, MinIO) 하드웨어 사양 확인
- OS 및 보안 설정
- PostgreSQL 16 설치 및 DB 스키마
- MinIO Object Lock 버킷 설정
- LibreOffice headless 및 한글 폰트
- Spring Boot 애플리케이션 배포
- Nginx TLS 설정
- 백업 스케줄 설정
- DB 역할 권한 분리 (audit_role)
- NTP 서버 동기화

**IQ 수행자**: 시스템 관리자 또는 인프라 담당자  
**IQ 검토자**: QA  
**IQ 수행 환경**: 운영 서버 (Production)

---

## 2. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-URS-001 v0.3 | User Requirements Specification |
| VAL-DS-001 v0.1 | Design Specification |
| VAL-TM-001 v0.1 | Traceability Matrix |
| VAL-VP-001 | Validation Plan |

---

## 3. IQ 수행 전제조건

IQ 수행 전 아래 항목이 완료되어 있어야 한다.

| # | 전제조건 | 확인 | 확인자 | 날짜 |
|---|---|---|---|---|
| PC-01 | 서버 하드웨어 랙 설치 완료 | ☐ | | |
| PC-02 | 사내망 네트워크 케이블 연결 완료 | ☐ | | |
| PC-03 | 사내 CA 인증서 발급 완료 | ☐ | | |
| PC-04 | 설치 매체(ISO, 패키지)가 사내 Nexus에 미러됨 | ☐ | | |
| PC-05 | IQ 프로토콜이 QA 승인 완료 상태 | ☐ | | |
| PC-06 | DB 초기 비밀번호 등 민감 정보가 금고에 보관됨 | ☐ | | |

---

## 4. IQ 체크리스트 — 인프라 하드웨어

### IQ-HW-001: App 서버 사양

| 항목 | 예상 사양 | 실제 측정값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| CPU 코어 수 | ≥ 8코어 | | ≥ 8코어 | ☐ Pass ☐ Fail | | |
| 메모리(RAM) | ≥ 16 GB | | ≥ 16 GB | ☐ Pass ☐ Fail | | |
| 스토리지 | ≥ 100 GB SSD | | ≥ 100 GB | ☐ Pass ☐ Fail | | |

**명령어**: `lscpu | grep 'CPU(s)'`, `free -h`, `df -h /`

---

### IQ-HW-002: DB 서버 사양

| 항목 | 예상 사양 | 실제 측정값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| CPU 코어 수 | ≥ 8코어 | | ≥ 8코어 | ☐ Pass ☐ Fail | | |
| 메모리(RAM) | ≥ 32 GB | | ≥ 32 GB | ☐ Pass ☐ Fail | | |
| 스토리지 | ≥ 500 GB SSD | | ≥ 500 GB | ☐ Pass ☐ Fail | | |

---

### IQ-HW-003: MinIO 서버 스토리지

| 항목 | 예상 사양 | 실제 측정값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 가용 스토리지 | ≥ 10 TB | | ≥ 10 TB | ☐ Pass ☐ Fail | | |
| RAID 구성 | RAID 6 이상 | | RAID 6+ | ☐ Pass ☐ Fail | | |

---

## 5. IQ 체크리스트 — OS 및 기본 설정

### IQ-OS-001: OS 버전

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| OS 배포판·버전 | Rocky Linux 9.x | | Rocky Linux 9.x | ☐ Pass ☐ Fail | | |
| 커널 버전 | 5.14+ | | 5.14+ | ☐ Pass ☐ Fail | | |

**명령어**: `cat /etc/redhat-release`, `uname -r`

---

### IQ-OS-002: 시스템 타임존 설정

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 타임존 | Asia/Seoul | | Asia/Seoul | ☐ Pass ☐ Fail | | |

**명령어**: `timedatectl`

---

### IQ-OS-003: 방화벽 설정

| 항목 | 예상 상태 | 실제 상태 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| firewalld 활성화 | 활성 | | 활성 | ☐ Pass ☐ Fail | | |
| 허용 포트 (App) | 443 (HTTPS) | | 443만 허용 | ☐ Pass ☐ Fail | | |
| 허용 포트 (DB) | 5432 (App 서버 IP만) | | 특정 IP만 허용 | ☐ Pass ☐ Fail | | |
| 허용 포트 (MinIO) | 9000 (App 서버 IP만) | | 특정 IP만 허용 | ☐ Pass ☐ Fail | | |

**명령어**: `firewall-cmd --list-all`

---

## 6. IQ 체크리스트 — PostgreSQL

### IQ-DB-001: PostgreSQL 버전

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| PostgreSQL 버전 | 16.x | | 16.x | ☐ Pass ☐ Fail | | |

**명령어**: `psql --version`

---

### IQ-DB-002: DB 스키마 마이그레이션 완료

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| Flyway 마이그레이션 상태 | 모두 Success | | 모두 Success | ☐ Pass ☐ Fail | | |
| 마이그레이션 스크립트 수 | 13개 (V001~V013) | | 13개 | ☐ Pass ☐ Fail | | |

**명령어**: `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;`

---

### IQ-DB-003: DB 연결 확인

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| app_role로 접속 | 접속 성공 | | 접속 성공 | ☐ Pass ☐ Fail | | |
| audit_role로 접속 | 접속 성공 | | 접속 성공 | ☐ Pass ☐ Fail | | |

---

### IQ-DB-004: WAL 아카이빙 설정

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| archive_mode | on | | on | ☐ Pass ☐ Fail | | |
| archive_command | 백업 경로 설정 | | 설정 완료 | ☐ Pass ☐ Fail | | |

**명령어**: `SHOW archive_mode; SHOW archive_command;`

---

## 7. IQ 체크리스트 — MinIO

### IQ-MINIO-001: MinIO 버전 및 실행 상태

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| MinIO 버전 | Object Lock 지원 버전 | | Object Lock 지원 | ☐ Pass ☐ Fail | | |
| 서비스 상태 | Running | | Running | ☐ Pass ☐ Fail | | |

**명령어**: `mc --version`, `systemctl status minio`

---

### IQ-MINIO-002: 버킷 생성 및 Object Lock 설정

각 버킷에 대해 아래 항목을 확인한다.

**버킷: edms-documents-original**

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 버킷 존재 여부 | 존재 | | 존재 | ☐ Pass ☐ Fail | | |
| Object Lock 활성화 | ENABLED | | ENABLED | ☐ Pass ☐ Fail | | |
| Versioning 상태 | Enabled | | Enabled | ☐ Pass ☐ Fail | | |
| 기본 보존 모드 | GOVERNANCE | | GOVERNANCE | ☐ Pass ☐ Fail | | |
| 기본 보존 기간 | 3650일 | | 3650일 | ☐ Pass ☐ Fail | | |

**버킷: edms-documents-rendition** — 동일 항목 확인

**버킷: edms-audit-anchors**

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 버킷 존재 여부 | 존재 | | 존재 | ☐ Pass ☐ Fail | | |
| Object Lock 활성화 | ENABLED | | ENABLED | ☐ Pass ☐ Fail | | |
| 기본 보존 모드 | **COMPLIANCE** | | **COMPLIANCE** | ☐ Pass ☐ Fail | | |
| 기본 보존 기간 | 3650일 | | 3650일 | ☐ Pass ☐ Fail | | |

**명령어**: `mc ls --json <alias>/edms-audit-anchors`, `mc retention info --recursive <alias>/edms-audit-anchors`

> ⚠️ **COMPLIANCE 모드 주의**: `edms-audit-anchors` 버킷의 Object Lock이 COMPLIANCE로 설정되면 보존 기간 내 MinIO 관리자도 삭제 불가. 설정 전 반드시 확인.

---

### IQ-MINIO-003: MinIO Object Lock 삭제 거부 검증 (Critical)

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| COMPLIANCE Lock 파일 삭제 시도 → 거부 | `AccessDenied` 오류 발생 | | `AccessDenied` 발생 | ☐ Pass ☐ Fail | | |

**절차**:
1. 테스트 파일을 `edms-audit-anchors`에 업로드
2. `mc rm <alias>/edms-audit-anchors/<test-file>` 실행
3. `AccessDenied` 또는 `Object Locked` 오류 확인

---

## 8. IQ 체크리스트 — LibreOffice

### IQ-LO-001: LibreOffice 버전

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| LibreOffice 버전 | 7.x | | 7.x | ☐ Pass ☐ Fail | | |
| headless 옵션 지원 | 지원 | | 지원 | ☐ Pass ☐ Fail | | |

**명령어**: `soffice --version`

---

### IQ-LO-002: 한글 폰트 설치 확인

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 나눔고딕 폰트 | 설치됨 | | 설치됨 | ☐ Pass ☐ Fail | | |
| 맑은 고딕(또는 대체 한글 폰트) | 설치됨 | | 설치됨 | ☐ Pass ☐ Fail | | |

**명령어**: `fc-list | grep -i nanum`, `fc-list | grep -i malgun`

---

### IQ-LO-003: 한글 DOCX → PDF 변환 테스트

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 한글 텍스트 포함 DOCX → PDF 변환 성공 | PDF 생성됨 | | PDF 파일 생성됨 | ☐ Pass ☐ Fail | | |
| 변환 PDF에서 한글 깨짐 없음 | 한글 정상 표시 | | 정상 표시 (육안 확인) | ☐ Pass ☐ Fail | | |

**명령어**: `soffice --headless --convert-to pdf --outdir /tmp/ test_korean.docx`

---

## 9. IQ 체크리스트 — Java / Spring Boot 애플리케이션

### IQ-APP-001: Java 버전

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| Java 버전 | 21.x (LTS) | | 21.x | ☐ Pass ☐ Fail | | |

**명령어**: `java -version`

---

### IQ-APP-002: 애플리케이션 기동 확인

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| Spring Boot 애플리케이션 기동 성공 | `Started Application` 로그 | | 정상 기동 | ☐ Pass ☐ Fail | | |
| 헬스체크 엔드포인트 응답 | HTTP 200 | | HTTP 200 | ☐ Pass ☐ Fail | | |
| DB 연결 (DataSource) 정상 | 오류 없음 | | 오류 없음 | ☐ Pass ☐ Fail | | |
| MinIO 연결 정상 | 오류 없음 | | 오류 없음 | ☐ Pass ☐ Fail | | |

**명령어**: `curl -k https://edms.internal/api/v1/actuator/health`

---

### IQ-APP-003: 애플리케이션 버전 확인

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 배포된 JAR 파일 버전 | 릴리즈 버전과 일치 | | 일치 | ☐ Pass ☐ Fail | | |
| 빌드 날짜·커밋 해시 (manifest) | 빌드 명세서와 일치 | | 일치 | ☐ Pass ☐ Fail | | |

---

## 10. IQ 체크리스트 — Nginx / TLS

### IQ-TLS-001: Nginx 버전 및 TLS 설정

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| Nginx 버전 | 1.26.x | | 1.26.x | ☐ Pass ☐ Fail | | |
| TLS 프로토콜 | TLSv1.2, TLSv1.3 | | TLSv1.2, TLSv1.3 | ☐ Pass ☐ Fail | | |
| SSL 인증서 발급 주체 | 사내 CA | | 사내 CA | ☐ Pass ☐ Fail | | |
| 인증서 유효기간 | 만료일 확인 | | 만료일 충분 | ☐ Pass ☐ Fail | | |
| HTTP → HTTPS 리다이렉트 | 301 리다이렉트 | | 301 발생 | ☐ Pass ☐ Fail | | |

**명령어**: `nginx -v`, `openssl s_client -connect edms.internal:443 | grep Protocol`

---

### IQ-TLS-002: 보안 HTTP 헤더 확인

| 헤더 | 예상 값 | 실제 값 | 결과 |
|---|---|---|---|
| Strict-Transport-Security | `max-age=31536000` | | ☐ Pass ☐ Fail |
| X-Content-Type-Options | `nosniff` | | ☐ Pass ☐ Fail |
| X-Frame-Options | `DENY` | | ☐ Pass ☐ Fail |
| Content-Security-Policy | 설정됨 | | ☐ Pass ☐ Fail |

**명령어**: `curl -I -k https://edms.internal/`

---

## 11. IQ 체크리스트 — 백업 설정

### IQ-BKP-001: DB 백업 스케줄 설정

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| pg_basebackup cron 등록 | 매일 00:00 | | `0 0 * * *` | ☐ Pass ☐ Fail | | |
| 백업 저장 경로 | 지정 경로 | | 경로 존재·쓰기 가능 | ☐ Pass ☐ Fail | | |

**명령어**: `crontab -l | grep pg_basebackup`

---

### IQ-BKP-002: 백업 수동 실행 테스트

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| pg_basebackup 수동 실행 | 백업 파일 생성됨 | | 생성됨 | ☐ Pass ☐ Fail | | |
| 백업 파일 크기 | 0보다 큼 | | > 0 bytes | ☐ Pass ☐ Fail | | |

---

### IQ-BKP-003: MinIO Site Replication 상태

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| Replication 상태 | 활성 (Active) | | Active | ☐ Pass ☐ Fail | | |
| DR 버킷에 파일 동기화 확인 | Primary와 동일 | | 동일 | ☐ Pass ☐ Fail | | |

**명령어**: `mc admin replicate info <alias>`

---

### IQ-BKP-004: 백업 무결성 검증

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| 백업 파일 SHA-256 기록 | 기록됨 | | 기록됨 | ☐ Pass ☐ Fail | | |

---

## 12. IQ 체크리스트 — 네트워크 및 보안

### IQ-NET-001: 폐쇄망 외부 통신 차단

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| App 서버에서 인터넷 접속 | 차단 (시간 초과) | | 차단 확인 | ☐ Pass ☐ Fail | | |
| Maven/npm 외부 저장소 접근 차단 | 차단 (사내 Nexus만 허용) | | 차단 확인 | ☐ Pass ☐ Fail | | |

**명령어**: `curl -m 5 https://google.com` (시간 초과 예상)

---

### IQ-NET-002: 사내 DNS 해석

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| edms.internal DNS 해석 | App 서버 IP 반환 | | 정확한 IP | ☐ Pass ☐ Fail | | |

---

## 13. IQ 체크리스트 — DB 역할 및 권한 분리

### IQ-DBROLE-001: app_role 권한 검증 (Critical)

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| app_role로 audit_logs INSERT | 성공 | | 성공 | ☐ Pass ☐ Fail | | |
| app_role로 audit_logs UPDATE 시도 | **거부 (permission denied)** | | 거부 확인 | ☐ Pass ☐ Fail | | |
| app_role로 audit_logs DELETE 시도 | **거부 (permission denied)** | | 거부 확인 | ☐ Pass ☐ Fail | | |
| app_role로 signature_manifests UPDATE 시도 | **거부** | | 거부 확인 | ☐ Pass ☐ Fail | | |
| app_role로 signature_manifests DELETE 시도 | **거부** | | 거부 확인 | ☐ Pass ☐ Fail | | |

**SQL 검증**:
```sql
-- app_role 세션에서 실행
SET ROLE app_role;
UPDATE audit_logs SET action = 'TAMPERED' WHERE id = 1;
-- 예상 결과: ERROR:  permission denied for table audit_logs

DELETE FROM signature_manifests WHERE id = 1;
-- 예상 결과: ERROR:  permission denied for table signature_manifests
```

---

### IQ-DBROLE-002: audit_role 권한 검증

| 항목 | 예상 결과 | 실제 결과 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| audit_role로 audit_logs INSERT | 성공 | | 성공 | ☐ Pass ☐ Fail | | |
| audit_role로 audit_logs SELECT | 성공 | | 성공 | ☐ Pass ☐ Fail | | |
| audit_role로 documents INSERT 시도 | **거부** | | 거부 확인 | ☐ Pass ☐ Fail | | |

---

## 14. IQ 체크리스트 — NTP 동기화

### IQ-NTP-001: NTP 동기화 상태

| 항목 | 예상 값 | 실제 값 | 합격 기준 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| chrony 활성화 상태 | active (running) | | active (running) | ☐ Pass ☐ Fail | | |
| 사내 NTP 서버 동기화 | Synchronized | | Synchronized | ☐ Pass ☐ Fail | | |
| 시각 오차 (offset) | ±1초 이내 | | ±1초 이내 | ☐ Pass ☐ Fail | | |

**명령어**: `chronyc tracking`, `chronyc sources`

---

## 15. IQ 요약 및 서명

### 15.1 IQ 항목 결과 집계

| 섹션 | 전체 항목 수 | Pass | Fail | N/A | 이탈 |
|---|---|---|---|---|---|
| IQ-HW (하드웨어) | 7 | | | | |
| IQ-OS (OS 설정) | 7 | | | | |
| IQ-DB (PostgreSQL) | 6 | | | | |
| IQ-MINIO (MinIO) | 11 | | | | |
| IQ-LO (LibreOffice) | 5 | | | | |
| IQ-APP (애플리케이션) | 6 | | | | |
| IQ-TLS (Nginx/TLS) | 6 | | | | |
| IQ-BKP (백업) | 6 | | | | |
| IQ-NET (네트워크) | 4 | | | | |
| IQ-DBROLE (DB 역할) | 8 | | | | |
| IQ-NTP (NTP) | 3 | | | | |
| **합계** | **69** | | | | |

### 15.2 IQ 합격 기준

- 모든 항목이 Pass이거나 N/A인 경우: **IQ 합격 → OQ 진행 가능**
- Fail 항목 존재 시: Deviation 기록 → 시정 조치 → 재검증

### 15.3 IQ 결론

```
☐ IQ 합격 — OQ 진행 승인
☐ 조건부 합격 — Deviation 해결 후 OQ 진행 (세부 내용: 이탈 기록 섹션 참조)
☐ IQ 불합격 — 시정 조치 필요
```

### 15.4 서명란

| 역할 | 이름 | 서명 | 날짜 |
|---|---|---|---|
| 수행자 (인프라 담당) | | | |
| 검토자 (QA) | | | |
| 승인자 (QA Manager) | | | |

---

## 16. 이탈(Deviation) 기록

IQ 수행 중 발견된 이탈 사항을 기록한다.

| 이탈 번호 | 항목 ID | 이탈 내용 | 영향 평가 | 시정 조치 | 완료 날짜 | 담당자 |
|---|---|---|---|---|---|---|
| DEV-IQ-001 | | | | | | |

이탈 없음 시: "이탈 없음 (No Deviations)" 기록.

---

## 17. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 초안 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA 승인 전까지 검증 수행에 사용할 수 없다.*
