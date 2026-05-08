# Installation Qualification Report (IQ 보고서)
## Electronic Document Management System (EDMS)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-IQ-001-R |
| 버전 | [작성 시 기입] |
| 작성일 | [IQ 완료일 기입] |
| 상태 | 초안 — 승인 전 |
| 검증 단계 | IQ (Installation Qualification) |
| 프로토콜 참조 | VAL-IQ-001 v[버전] (QA 승인 완료) |
| IQ 수행 기간 | [시작일] ~ [완료일] |
| IQ 수행 환경 | 운영 서버 (Production) |
| 수행자 | TBD (인프라 담당) |
| 검토자 | TBD (QA) |
| 승인자 | TBD (QA Manager) |

---

> **템플릿 사용 안내**
>
> 본 문서는 IQ Report 작성 템플릿이다. IQ 프로토콜(VAL-IQ-001)을 수행한 후 각 체크리스트의 실제 결과값을 기입하고, 이탈 발생 시 이탈 기록 섹션에 상세히 기록한다. `[괄호]` 항목은 수행자가 채워 넣으며, 작성 완료 후 본 안내 박스를 삭제한다.

---

## 목차

1. [목적 및 범위](#1-목적-및-범위)
2. [수행 전제조건 확인](#2-수행-전제조건-확인)
3. [IQ 결과 — 인프라 하드웨어](#3-iq-결과--인프라-하드웨어)
4. [IQ 결과 — OS 및 기본 설정](#4-iq-결과--os-및-기본-설정)
5. [IQ 결과 — PostgreSQL](#5-iq-결과--postgresql)
6. [IQ 결과 — MinIO](#6-iq-결과--minio)
7. [IQ 결과 — LibreOffice](#7-iq-결과--libreoffice)
8. [IQ 결과 — Java / Spring Boot 애플리케이션](#8-iq-결과--java--spring-boot-애플리케이션)
9. [IQ 결과 — Nginx / TLS](#9-iq-결과--nginx--tls)
10. [IQ 결과 — 백업 설정](#10-iq-결과--백업-설정)
11. [IQ 결과 — 네트워크 및 보안](#11-iq-결과--네트워크-및-보안)
12. [IQ 결과 — DB 역할 및 권한 분리](#12-iq-결과--db-역할-및-권한-분리)
13. [IQ 결과 — NTP 동기화](#13-iq-결과--ntp-동기화)
14. [이탈(Deviation) 기록](#14-이탈deviation-기록)
15. [IQ 결과 종합 및 결론](#15-iq-결과-종합-및-결론)
16. [서명란](#16-서명란)
17. [변경 이력](#17-변경-이력)

---

## 1. 목적 및 범위

본 보고서는 VAL-IQ-001(IQ 프로토콜)에 따라 수행된 EDMS 설치 적격성 검증 결과를 기록한다. 운영 환경의 하드웨어, OS, DB, 스토리지, 애플리케이션, 보안 설정이 DS(VAL-DS-001)에 정의된 사양을 충족함을 문서화한다.

---

## 2. 수행 전제조건 확인

| # | 전제조건 | 확인 | 확인자 | 날짜 |
|---|---|---|---|---|
| PC-01 | 서버 하드웨어 랙 설치 완료 | ☐ Yes ☐ No | | |
| PC-02 | 사내망 네트워크 케이블 연결 완료 | ☐ Yes ☐ No | | |
| PC-03 | 사내 CA 인증서 발급 완료 | ☐ Yes ☐ No | | |
| PC-04 | 설치 매체가 사내 Nexus에 미러됨 | ☐ Yes ☐ No | | |
| PC-05 | IQ 프로토콜 QA 승인 완료 상태 | ☐ Yes ☐ No | | |
| PC-06 | DB 초기 비밀번호 등 민감 정보 금고 보관 | ☐ Yes ☐ No | | |

모든 전제조건 충족 여부: ☐ **Yes — IQ 수행 가능** ☐ No (미충족 항목: [기입])

---

## 3. IQ 결과 — 인프라 하드웨어

### IQ-HW-001: App 서버 사양

| 항목 | 합격 기준 | 실제 측정값 | 명령어/근거 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|---|
| CPU 코어 수 | ≥ 8코어 | [N]코어 | `lscpu` | ☐ Pass ☐ Fail | | |
| 메모리(RAM) | ≥ 16 GB | [N] GB | `free -h` | ☐ Pass ☐ Fail | | |
| 스토리지 | ≥ 100 GB SSD | [N] GB | `df -h /` | ☐ Pass ☐ Fail | | |

### IQ-HW-002: DB 서버 사양

| 항목 | 합격 기준 | 실제 측정값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| CPU 코어 수 | ≥ 8코어 | [N]코어 | ☐ Pass ☐ Fail | | |
| 메모리(RAM) | ≥ 32 GB | [N] GB | ☐ Pass ☐ Fail | | |
| 스토리지 | ≥ 500 GB SSD | [N] GB | ☐ Pass ☐ Fail | | |

### IQ-HW-003: MinIO 서버 스토리지

| 항목 | 합격 기준 | 실제 측정값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| 가용 스토리지 | ≥ 10 TB | [N] TB | ☐ Pass ☐ Fail | | |
| RAID 구성 | RAID 6 이상 | [RAID 레벨] | ☐ Pass ☐ Fail | | |

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 4. IQ 결과 — OS 및 기본 설정

### IQ-OS-001: OS 버전

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| OS 배포판·버전 | Rocky Linux 9.x | [실제 버전] | ☐ Pass ☐ Fail | | |
| 커널 버전 | 5.14+ | [실제 버전] | ☐ Pass ☐ Fail | | |

**실행 명령어 및 출력**:
```
$ cat /etc/redhat-release
[실제 출력 붙여넣기]

$ uname -r
[실제 출력 붙여넣기]
```

### IQ-OS-002: 시스템 타임존

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| 타임존 | Asia/Seoul | [실제 값] | ☐ Pass ☐ Fail | | |

### IQ-OS-003: 방화벽 설정

| 항목 | 합격 기준 | 실제 상태 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| firewalld 활성화 | 활성 | [실제 상태] | ☐ Pass ☐ Fail | | |
| 허용 포트 (App) | 443만 허용 | [실제 설정] | ☐ Pass ☐ Fail | | |
| 허용 포트 (DB) | 특정 IP만 허용 | [실제 설정] | ☐ Pass ☐ Fail | | |
| 허용 포트 (MinIO) | 특정 IP만 허용 | [실제 설정] | ☐ Pass ☐ Fail | | |

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 5. IQ 결과 — PostgreSQL

### IQ-DB-001: PostgreSQL 버전

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| PostgreSQL 버전 | 16.x | [실제 버전] | ☐ Pass ☐ Fail | | |

### IQ-DB-002: DB 스키마 마이그레이션

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Flyway 마이그레이션 상태 | 모두 Success | [상태] | ☐ Pass ☐ Fail | | |
| 마이그레이션 스크립트 수 | 13개 | [N]개 | ☐ Pass ☐ Fail | | |

**Flyway 조회 결과**:
```
[SELECT version, description, success FROM flyway_schema_history 실행 결과 붙여넣기]
```

### IQ-DB-003: DB 연결 확인

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| app_role 접속 | 접속 성공 | [결과] | ☐ Pass ☐ Fail | | |
| audit_role 접속 | 접속 성공 | [결과] | ☐ Pass ☐ Fail | | |

### IQ-DB-004: WAL 아카이빙 설정

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| archive_mode | on | [실제 값] | ☐ Pass ☐ Fail | | |
| archive_command | 백업 경로 설정 | [실제 값] | ☐ Pass ☐ Fail | | |

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 6. IQ 결과 — MinIO

### IQ-MINIO-001: MinIO 버전 및 실행 상태

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| MinIO 버전 | Object Lock 지원 버전 | [실제 버전] | ☐ Pass ☐ Fail | | |
| 서비스 상태 | Running | [실제 상태] | ☐ Pass ☐ Fail | | |

### IQ-MINIO-002: 버킷 Object Lock 설정

**버킷: edms-documents-original**

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| 버킷 존재 여부 | 존재 | [존재/없음] | ☐ Pass ☐ Fail | | |
| Object Lock 활성화 | ENABLED | [실제 값] | ☐ Pass ☐ Fail | | |
| Versioning 상태 | Enabled | [실제 값] | ☐ Pass ☐ Fail | | |
| 보존 모드 | GOVERNANCE | [실제 값] | ☐ Pass ☐ Fail | | |
| 보존 기간 | 3650일 | [실제 값] | ☐ Pass ☐ Fail | | |

**버킷: edms-documents-rendition** — 동일 항목 확인

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Object Lock 활성화 | ENABLED | [실제 값] | ☐ Pass ☐ Fail | | |
| 보존 모드 | GOVERNANCE | [실제 값] | ☐ Pass ☐ Fail | | |
| 보존 기간 | 3650일 | [실제 값] | ☐ Pass ☐ Fail | | |

**버킷: edms-audit-anchors** ⚠️ COMPLIANCE 모드 확인 필수

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Object Lock 활성화 | ENABLED | [실제 값] | ☐ Pass ☐ Fail | | |
| **보존 모드** | **COMPLIANCE** | [실제 값] | ☐ Pass ☐ Fail | | |
| 보존 기간 | 3650일 | [실제 값] | ☐ Pass ☐ Fail | | |

### IQ-MINIO-003: Object Lock 삭제 거부 검증 (Critical)

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| COMPLIANCE Lock 파일 삭제 시도 → 거부 | `AccessDenied` 오류 발생 | [실제 오류 메시지] | ☐ Pass ☐ Fail | | |

**실행 명령어 및 출력**:
```
$ mc rm <alias>/edms-audit-anchors/<test-file>
[실제 출력 붙여넣기]
```

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 7. IQ 결과 — LibreOffice

### IQ-LO-001: LibreOffice 버전

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| LibreOffice 버전 | 7.x | [실제 버전] | ☐ Pass ☐ Fail | | |
| headless 옵션 지원 | 지원 | [확인 결과] | ☐ Pass ☐ Fail | | |

### IQ-LO-002: 한글 폰트 설치

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| 나눔고딕 폰트 | 설치됨 | [설치 경로] | ☐ Pass ☐ Fail | | |
| 한글 대체 폰트 | 설치됨 | [설치 폰트명] | ☐ Pass ☐ Fail | | |

### IQ-LO-003: 한글 DOCX → PDF 변환 테스트

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| 한글 DOCX → PDF 변환 성공 | PDF 파일 생성됨 | [결과] | ☐ Pass ☐ Fail | | |
| 한글 깨짐 없음 | 정상 표시 (육안 확인) | [결과] | ☐ Pass ☐ Fail | | |

**테스트 파일명**: [파일명]  
**변환 결과 PDF 보관 위치**: [경로]

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 8. IQ 결과 — Java / Spring Boot 애플리케이션

### IQ-APP-001: Java 버전

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Java 버전 | 21.x (LTS) | [실제 버전] | ☐ Pass ☐ Fail | | |

### IQ-APP-002: 애플리케이션 기동 확인

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Spring Boot 기동 성공 | `Started Application` 로그 | [확인 결과] | ☐ Pass ☐ Fail | | |
| 헬스체크 엔드포인트 | HTTP 200 | HTTP [응답 코드] | ☐ Pass ☐ Fail | | |
| DB 연결 정상 | 오류 없음 | [확인 결과] | ☐ Pass ☐ Fail | | |
| MinIO 연결 정상 | 오류 없음 | [확인 결과] | ☐ Pass ☐ Fail | | |

### IQ-APP-003: 애플리케이션 버전 확인

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| JAR 파일 버전 | 릴리즈 버전과 일치 | [실제 버전] | ☐ Pass ☐ Fail | | |
| 빌드 날짜·커밋 해시 | 빌드 명세서와 일치 | [실제 값] | ☐ Pass ☐ Fail | | |

**배포된 JAR 파일명**: [파일명]  
**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 9. IQ 결과 — Nginx / TLS

### IQ-TLS-001: Nginx 버전 및 TLS 설정

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Nginx 버전 | 1.26.x | [실제 버전] | ☐ Pass ☐ Fail | | |
| TLS 프로토콜 | TLSv1.2, TLSv1.3 | [실제 프로토콜] | ☐ Pass ☐ Fail | | |
| SSL 인증서 발급 주체 | 사내 CA | [발급 주체] | ☐ Pass ☐ Fail | | |
| 인증서 유효기간 | 충분한 잔여 기간 | [만료일] | ☐ Pass ☐ Fail | | |
| HTTP → HTTPS 리다이렉트 | 301 리다이렉트 | HTTP [응답 코드] | ☐ Pass ☐ Fail | | |

### IQ-TLS-002: 보안 HTTP 헤더

| 헤더 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Strict-Transport-Security | `max-age=31536000` | [실제 값] | ☐ Pass ☐ Fail | | |
| X-Content-Type-Options | `nosniff` | [실제 값] | ☐ Pass ☐ Fail | | |
| X-Frame-Options | `DENY` | [실제 값] | ☐ Pass ☐ Fail | | |
| Content-Security-Policy | 설정됨 | [실제 값] | ☐ Pass ☐ Fail | | |

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 10. IQ 결과 — 백업 설정

### IQ-BKP-001: DB 백업 스케줄

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| pg_basebackup cron 등록 | 매일 00:00 | [cron 표현식] | ☐ Pass ☐ Fail | | |
| 백업 저장 경로 | 쓰기 가능한 경로 | [실제 경로] | ☐ Pass ☐ Fail | | |

### IQ-BKP-002: 백업 수동 실행 테스트

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| pg_basebackup 수동 실행 | 백업 파일 생성됨 | [결과] | ☐ Pass ☐ Fail | | |
| 백업 파일 크기 | > 0 bytes | [N] bytes | ☐ Pass ☐ Fail | | |

### IQ-BKP-003: MinIO Site Replication

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| Replication 상태 | Active | [실제 상태] | ☐ Pass ☐ Fail | | |
| DR 버킷 파일 동기화 | Primary와 동일 | [확인 결과] | ☐ Pass ☐ Fail | | |

### IQ-BKP-004: 백업 무결성 검증

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| 백업 파일 SHA-256 기록 | 기록됨 | [확인 결과] | ☐ Pass ☐ Fail | | |

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 11. IQ 결과 — 네트워크 및 보안

### IQ-NET-001: 폐쇄망 외부 통신 차단

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| App 서버 인터넷 접속 차단 | 시간 초과 | [실제 결과] | ☐ Pass ☐ Fail | | |
| 외부 Maven/npm 저장소 차단 | 차단 확인 | [실제 결과] | ☐ Pass ☐ Fail | | |

### IQ-NET-002: 사내 DNS 해석

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| edms.internal DNS 해석 | App 서버 IP 반환 | [IP 주소] | ☐ Pass ☐ Fail | | |

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 12. IQ 결과 — DB 역할 및 권한 분리

### IQ-DBROLE-001: app_role 권한 검증 (Critical)

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| app_role → audit_logs INSERT | 성공 | [결과] | ☐ Pass ☐ Fail | | |
| app_role → audit_logs UPDATE 시도 | **거부 (permission denied)** | [실제 오류] | ☐ Pass ☐ Fail | | |
| app_role → audit_logs DELETE 시도 | **거부** | [실제 오류] | ☐ Pass ☐ Fail | | |
| app_role → signature_manifests UPDATE 시도 | **거부** | [실제 오류] | ☐ Pass ☐ Fail | | |
| app_role → signature_manifests DELETE 시도 | **거부** | [실제 오류] | ☐ Pass ☐ Fail | | |

**SQL 실행 결과**:
```sql
-- app_role 세션
SET ROLE app_role;
UPDATE audit_logs SET action = 'TAMPERED' WHERE id = 1;
-- 실제 출력: [붙여넣기]

DELETE FROM signature_manifests WHERE id = 1;
-- 실제 출력: [붙여넣기]
```

### IQ-DBROLE-002: audit_role 권한 검증

| 항목 | 합격 기준 | 실제 결과 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| audit_role → audit_logs INSERT | 성공 | [결과] | ☐ Pass ☐ Fail | | |
| audit_role → audit_logs SELECT | 성공 | [결과] | ☐ Pass ☐ Fail | | |
| audit_role → documents INSERT 시도 | **거부** | [실제 오류] | ☐ Pass ☐ Fail | | |

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 13. IQ 결과 — NTP 동기화

### IQ-NTP-001: NTP 동기화 상태

| 항목 | 합격 기준 | 실제 값 | 결과 | 수행자 | 날짜 |
|---|---|---|---|---|---|
| chrony 활성화 상태 | active (running) | [실제 상태] | ☐ Pass ☐ Fail | | |
| 사내 NTP 서버 동기화 | Synchronized | [실제 상태] | ☐ Pass ☐ Fail | | |
| 시각 오차 (offset) | ±1초 이내 | ±[N]ms | ☐ Pass ☐ Fail | | |

**chronyc tracking 출력**:
```
[실제 출력 붙여넣기]
```

**섹션 소견**: [이상 없음 또는 특이 사항 기술]

---

## 14. 이탈(Deviation) 기록

IQ 수행 중 발견된 모든 이탈을 기록한다. 이탈 없는 경우: **"이탈 없음 (No Deviations)"** 기재.

| 이탈 번호 | 항목 ID | 이탈 내용 | 등급 | 영향 평가 | 시정 조치 | 재검증 결과 | 종결일 | 담당자 |
|---|---|---|---|---|---|---|---|---|
| DEV-IQ-001 | | | Critical / Major / Minor | | | | | |

---

## 15. IQ 결과 종합 및 결론

### 15.1 항목별 결과 집계

| 섹션 | 전체 항목 | Pass | Fail | N/A | 이탈 |
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

### 15.2 결론

[작성 예시]
> IQ 수행 결과, 총 69개 항목 중 [N]개 Pass, [N]개 N/A, Fail [N]개가 확인되었다.
> Fail 항목에 대해 이탈 [DEV-IQ-XXX]이 기록되었으며, [시정 조치 내용]으로 해결되어 재검증 결과 합격하였다.
> 모든 이탈이 종결되었으므로 IQ 합격으로 판정하고, OQ 진행을 승인한다.

[실제 결과에 따라 수정하여 작성한다.]

```
☐ IQ 합격 — OQ 진행 승인
☐ 조건부 합격 — Deviation [DEV-IQ-XXX] 해결 완료 확인 후 OQ 진행
☐ IQ 불합격 — 시정 조치 후 IQ 재수행 필요
```

---

## 16. 서명란

| 역할 | 이름 | 서명 | 날짜 |
|---|---|---|---|
| 수행자 (인프라 담당) | | | |
| 검토자 (QA) | | | |
| 승인자 (QA Manager) | | | |

---

## 17. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 (템플릿) | 2026-05-08 | 최초 템플릿 작성 | TBD |
| [1.0] | [완료일] | IQ 수행 결과 기록 | [수행자] |

---

*수행자 서명이 완료되기 전까지 본 보고서는 초안(Draft) 상태이다.*
