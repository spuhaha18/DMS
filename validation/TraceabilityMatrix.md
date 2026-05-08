# Traceability Matrix (추적성 매트릭스)
## Electronic Document Management System (EDMS)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-TM-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| URS 참조 | VAL-URS-001 v0.3 |
| FS 참조 | VAL-FS-001 v0.1 |
| DS 참조 | VAL-DS-001 v0.1 |
| RA 참조 | VAL-RA-001 v0.1 |
| 작성자 | TBD |
| 검토자 | TBD |
| 승인자 | TBD (QA) |

---

## 목차

1. [목적 및 사용 방법](#1-목적-및-사용-방법)
2. [URS → FS → DS → 테스트 추적성 매트릭스](#2-urs--fs--ds--테스트-추적성-매트릭스)
3. [테스트 케이스 커버리지 요약](#3-테스트-케이스-커버리지-요약)
4. [역방향 추적성 (테스트 → URS)](#4-역방향-추적성-테스트--urs)
5. [변경 이력](#5-변경-이력)

> §2.16 운영 SOP 추적성(OSOP) 포함 — SOP-AUDIT-TRAIL-001/DATA-INTEGRITY-001/DEVIATION-001/INCIDENT-001/TRAINING-001

---

## 1. 목적 및 사용 방법

본 매트릭스는 URS 요구사항이 FS 기능명세 → DS 설계 → 테스트(OQ/PQ)로 빠짐없이 추적됨을 증명한다. GxP 감사에서 가장 먼저 요청되는 문서이며, 다음 두 방향의 완결성을 보장한다.

- **순방향 추적성**: URS 요구사항 → FS → DS → 테스트 케이스 (요구사항이 구현·검증됨을 증명)
- **역방향 추적성**: 테스트 케이스 → URS (불필요한 기능이 없음을 증명)

**열 설명**:
- **URS ID**: VAL-URS-001의 요구사항 ID
- **FS 항목**: VAL-FS-001의 대응 기능 명세
- **DS 섹션**: VAL-DS-001의 대응 설계 섹션
- **RA 위험 ID**: VAL-RA-001에서 연관된 위험 항목 (있는 경우)
- **OQ 케이스**: OQ 프로토콜에서 검증할 테스트 케이스 ID
- **PQ 케이스**: PQ에서 확인할 케이스 (필요 시)
- **Critical**: Critical 위험 항목 여부 (●=Critical)

> **참고**: OQ/PQ 케이스 ID는 각 프로토콜(`VAL-OQ-001`, `VAL-PQ-001`) 작성 시 확정된다. 본 매트릭스에서는 케이스 코드 체계를 사전 정의하여 추적성 연결 고리를 유지한다.

---

## 2. URS → FS → DS → 테스트 추적성 매트릭스

### 2.1 인증 및 세션 (AUTH)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-AUTH-001 | 사용자 ID/PW 로그인 | FS-AUTH-001 | §5.1, §6.2, §8.4 | RA-AUTH-001 | OQ-AUTH-001, OQ-AUTH-002 | ● |
| UR-AUTH-002 | 비밀번호 정책 (길이·복잡도·이력) | FS-AUTH-002 | §5.1, §8.4, §4.2 password_history | RA-AUTH-005 | OQ-AUTH-006, OQ-AUTH-007, OQ-AUTH-008 | |
| UR-AUTH-003 | 연속 5회 실패 시 계정 잠금 | FS-AUTH-001 (BR-AUTH-004) | §5.1 LocalAuthProvider | RA-AUTH-002 | OQ-AUTH-003, OQ-AUTH-004, OQ-AUTH-005 | ● |
| UR-AUTH-004 | 15분 비활성 세션 자동 만료 | FS-AUTH-003 | §5.1 SessionManager, §9.2 | RA-AUTH-003 | OQ-AUTH-009, OQ-AUTH-010 | |
| UR-AUTH-005 | 인증 공급자 추상화 (Phase 2 AD 대비) | FS-AUTH-004 | §5.1 AuthProvider interface | — | OQ-AUTH-011 | |
| UR-AUTH-006 | 단일 세션 강제 | FS-AUTH-003 (BR-AUTH-015) | §5.1 maximumSessions(1) | RA-AUTH-004 | OQ-AUTH-012, OQ-AUTH-013 | |

### 2.2 사용자 및 역할 관리 (USER)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-USER-001 | 관리자 계정 생성 (CRUD) | FS-USER-001 | §4.2 users DDL, §6.7 | — | OQ-USER-001, OQ-USER-002 | |
| UR-USER-002 | 계정 비활성화 (삭제 불가) | FS-USER-002 | §4.2 users.status, §6.7 | — | OQ-USER-003, OQ-USER-004 | |
| UR-USER-003 | 8개 기본 역할 정의 | FS-USER-003 | §4.2 roles DDL, §6.7 | — | OQ-USER-005 | |
| UR-USER-004 | 사용자-역할 배정·회수 | FS-USER-003 | §4.2 user_roles DDL | — | OQ-USER-006, OQ-USER-007 | |
| UR-USER-005 | Auditor 계정 유효기간 | FS-USER-004 | §4.2 valid_until, §5.1 | — | OQ-USER-008, OQ-USER-009 | |

### 2.3 접근 통제 (ACC)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-ACC-001 | 3차원 RBAC (역할×카테고리×범위) | FS-ACC-001 | §4.2 permissions DDL, §9.4 | RA-ACC-001 | OQ-ACC-001~OQ-ACC-006 | ● |
| UR-ACC-002 | 기밀 프로젝트 문서 격리 | FS-ACC-002 | §4.2 documents.confidential | RA-ACC-003 | OQ-ACC-007, OQ-ACC-008 | |
| UR-ACC-003 | 권한별 다운로드 통제 | FS-ACC-003 | §7.3, §6.3 /pdf/download | RA-ACC-002 | OQ-ACC-009, OQ-ACC-010 | |
| UR-ACC-004 | 권한 변경 즉시 적용 | FS-ACC-001 (BR-ACC-004) | §5.1 캐시 미사용 | RA-ACC-004 | OQ-ACC-011 | |

### 2.4 문서 관리 (DOC)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-DOC-001 | SOP/Method/Spec/Form 문서 카테고리 | FS-DOC-001 | §4.2 document_categories DDL | — | OQ-DOC-001 | |
| UR-DOC-002 | 카테고리별 채번 템플릿 자동 채번 | FS-DOC-001 | §5.2 NumberingService, §4.2 numbering_* | RA-DOC-003 | OQ-DOC-002, OQ-DOC-003, OQ-DOC-004 | |
| UR-DOC-003 | Office 파일 업로드 | FS-DOC-002 | §5.4, §9.3 화이트리스트 | — | OQ-DOC-005 | |
| UR-DOC-004 | LibreOffice PDF 자동 변환 | FS-DOC-003 | §5.4 PdfConversionService | RA-DOC-002 | OQ-DOC-006, OQ-DOC-007 | ● |
| UR-DOC-005 | 워터마크 삽입 | FS-DOC-004 | §5.4 WatermarkService, §8.5 | — | OQ-DOC-008 | |
| UR-DOC-006 | pdf.js 뷰어 인브라우저 열람 | FS-DOC-005 | §7.3 pdf.js 설정 | — | OQ-DOC-009 | |
| UR-DOC-007 | Major-only 버전 관리 (Rev 0/1/2) | FS-DOC-006 | §4.2 document_versions.revision | — | OQ-DOC-010 | |
| UR-DOC-008 | 개정 시작 (신규 Draft 생성) | FS-DOC-007 | §5.3 T-06 전이 | — | OQ-DOC-011 | |
| UR-DOC-009 | 문서 메타데이터 편집 | FS-DOC-008 | §4.2 document_versions, §6.3 | — | OQ-DOC-012 | |
| UR-DOC-010 | 파일 형식·크기 제한 | FS-DOC-009 | §9.3 업로드 화이트리스트 | — | OQ-DOC-013, OQ-DOC-014 | |

### 2.5 문서 라이프사이클 (LCY)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-LCY-001 | 7단계 한국어 상태 정의 | FS-LCY-001 | §3.2, §5.3 상태 코드 | — | OQ-LCY-001 | |
| UR-LCY-002 | 상태 전이 매트릭스 (허용/금지) | FS-LCY-001 | §5.3 LifecycleStateMachine T-01~T-09 | RA-LCY-001 | OQ-LCY-002~OQ-LCY-018 | ● |
| UR-LCY-003 | Author 검토 제출 (T-01) | FS-LCY-001, FS-LCY-003 | §5.3 T-01, §6.4 /submit | RA-LCY-001 | OQ-LCY-002 | ● |
| UR-LCY-004 | 검토 완료 → 승인중 전이 (T-02) | FS-LCY-001, FS-LCY-004 | §5.3 T-02 | RA-LCY-001, RA-LCY-002 | OQ-LCY-004, OQ-LCY-005 | ● |
| UR-LCY-005 | 승인 완료 → 시행중 전이 (T-03) | FS-LCY-001, FS-LCY-005 | §5.3 T-03, qa_mandatory | RA-LCY-001, RA-LCY-003 | OQ-LCY-006, OQ-LCY-007 | ● |
| UR-LCY-006 | 반려 → 초안 복귀 (T-04, T-05) | FS-LCY-001, FS-LCY-006 | §5.3 T-04, T-05 | RA-LCY-005 | OQ-LCY-008, OQ-LCY-009 | |
| UR-LCY-007 | 카테고리별 워크플로 템플릿 설정 | FS-LCY-002, FS-LCY-007 | §4.2 workflow_template_steps, §6.7 | — | OQ-LCY-010, OQ-LCY-011 | |
| UR-LCY-008 | 워크플로 인스턴스 자동 생성 | FS-LCY-003 | §4.2 workflow_instances, §5.3 | — | OQ-LCY-012 | |
| UR-LCY-009 | 병렬 검토 지원 | FS-LCY-009 | §4.2 parallel 컬럼, §5.3 | — | OQ-LCY-013 | |

### 2.6 전자서명 (SIG)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-SIG-001 | Part 11 §11.200 전자서명 준수 | FS-SIG-001, FS-SIG-003 | §6.5, §8.1 | RA-SIG-001, RA-SIG-006 | OQ-SIG-001~OQ-SIG-010 | ● |
| UR-SIG-002 | 세션 첫 서명: ID+PW / 이후: PW만 | FS-SIG-003 | §6.5 meaning 분기 로직 | RA-SIG-006 | OQ-SIG-009, OQ-SIG-010 | ● |
| UR-SIG-003 | 서명 시 비밀번호 재인증 | FS-SIG-004 | §6.5 password 필드 필수 | RA-SIG-001 | OQ-SIG-001, OQ-SIG-002 | ● |
| UR-SIG-004 | 서명 매니페스트 기록 (서명자·의미·시점·IP) | FS-SIG-005 | §4.2 signature_manifests DDL | RA-SIG-004 | OQ-SIG-003 | ● |
| UR-SIG-005 | SHA-256 해시체인 (변조 탐지) | FS-SIG-002 | §8.1 해시체인 알고리즘 | RA-SIG-002, RA-SIG-003 | OQ-SIG-004~OQ-SIG-008 | ● |
| UR-SIG-006 | 서버 타임스탬프 사용 | FS-SIG-001 (BR) | §10.5 NTP, §4.2 server_ts | RA-SIG-005 | OQ-SIG-011 | |
| UR-SIG-007 | 서명 블록 PDF stamp | FS-SIG-006 | §5.4 WatermarkService | — | OQ-SIG-012 | |
| UR-SIG-008 | 서명 조회 및 내보내기 | FS-SIG-007 | §6.5 GET /signatures | — | OQ-SIG-013 | |

### 2.7 감사추적 (AUD)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-AUD-001 | ALCOA+ 원칙 준수 감사로그 | FS-AUD-001 | §5.5 AuditService | RA-AUD-004 | OQ-AUD-008~OQ-AUD-015 | ● |
| UR-AUD-002 | INSERT-only (수정·삭제 불가) | FS-AUD-002 | §4.3 DB 역할 분리 | RA-AUD-001 | OQ-AUD-001~OQ-AUD-003 | ● |
| UR-AUD-003 | 감사로그 해시체인 | FS-AUD-001 | §8.2 감사로그 해시체인 알고리즘 | RA-AUD-002 | OQ-AUD-004, OQ-AUD-005 | ● |
| UR-AUD-004 | 일별 WORM 앵커링 (MinIO Object Lock) | FS-AUD-003 | §8.3 앵커링 알고리즘, §10.2 MinIO | RA-AUD-003 | OQ-AUD-006, OQ-AUD-007 | ● |
| UR-AUD-005 | 해시체인 무결성 검증 API | FS-AUD-004 | §6.6 POST /checkpoints/verify | RA-AUD-002 | OQ-AUD-005 | ● |
| UR-AUD-006 | 감사로그 조회 및 내보내기 | FS-AUD-005 | §6.6 GET /audit-logs | — | OQ-AUD-017, OQ-AUD-018 | |
| UR-AUD-007 | 10년 이상 보관 | FS-AUD-007 | §10.2 MinIO COMPLIANCE 3650일 | RA-AUD-003, RA-AUD-005 | OQ-AUD-016 | ● |

### 2.8 검색 및 조회 (SRCH)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-SRCH-001 | 메타데이터 + 전문 통합 검색 | FS-SRCH-001 | §6.9, §4.2 tsvector | — | OQ-SRCH-001, OQ-SRCH-002 | |
| UR-SRCH-002 | 한국어 형태소 분석 (mecab-ko) | FS-SRCH-002 | §4.2 mecab-ko TEXT SEARCH CONFIG | — | OQ-SRCH-003 | |
| UR-SRCH-003 | 카테고리·부서·상태 필터 검색 | FS-SRCH-003 | §6.9 쿼리 파라미터 | — | OQ-SRCH-004 | |
| UR-SRCH-004 | 검색 결과 권한 필터 | FS-SRCH-004 | §6.9 권한 필터 | — | OQ-SRCH-005 | |
| UR-SRCH-005 | 검색 결과 하이라이트·정렬 | FS-SRCH-005 | §6.9 ts_rank | — | OQ-SRCH-006 | |

### 2.9 알림 및 정기검토 (NTFY)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-NTFY-001 | SMTP 이메일 알림 | FS-NTFY-001 | §5.6 EmailNotificationService | — | OQ-NTFY-001 | |
| UR-NTFY-002 | 앱 내 SSE 실시간 알림 | FS-NTFY-002 | §5.6 SseNotificationService, §6.8 | — | OQ-NTFY-002 | |
| UR-NTFY-003 | 알림 읽음 처리 | FS-NTFY-003 | §6.8 PUT /read | — | OQ-NTFY-003 | |
| UR-NTFY-004 | 정기검토 90/30/7/0일 알림 | FS-NTFY-004 | §5.7 PeriodicReviewScheduler | — | OQ-NTFY-004, OQ-NTFY-005 | |

### 2.10 교육 및 배포 (TRN)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-TRN-001 | 시행 문서 교육 과제 배정 | FS-TRN-001 | §4.2 training_assignments | — | OQ-TRN-001 | |
| UR-TRN-002 | Read & Acknowledge 서명 | FS-TRN-002 | §4.2 completion_sig_id, §6.5 | — | OQ-TRN-002 | |
| UR-TRN-003 | 교육 완료 조건 및 이력 | FS-TRN-003 | §4.2 completed_at | — | OQ-TRN-003 | |
| UR-TRN-004 | 교육 미완료 기한 알림 | FS-TRN-004 | §5.7 due_at 알림 | — | OQ-TRN-004 | |

### 2.11 시스템 관리 (ADMIN)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-ADMIN-001 | 문서 카테고리 마스터 관리 | FS-ADMIN-001 | §6.7 Admin API, §4.2 categories | — | OQ-ADMIN-001 | |
| UR-ADMIN-002 | 채번 템플릿 관리 | FS-ADMIN-002 | §6.7, §5.2 NumberingService | — | OQ-ADMIN-002 | |
| UR-ADMIN-003 | 워크플로 템플릿 관리 | FS-ADMIN-003 | §6.7, §4.2 workflow_template_steps | — | OQ-ADMIN-003 | |

### 2.12 성능 및 가용성 (PERF)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-PERF-001 | API 응답 시간 < 2초 (목록·검색) | FS-PERF-001 | §4.2 인덱스, §3.1 아키텍처 | — | OQ-PERF-001, OQ-PERF-002 | |
| UR-PERF-002 | 동시 사용자 100명 지원 | FS-PERF-002 | §3.1 단일 App 서버 | — | OQ-PERF-003 | |
| UR-PERF-003 | PDF 변환 5분 내 완료 | FS-PERF-003 | §5.4 비동기 큐 | — | OQ-PERF-004 | |

### 2.13 보안 (SEC)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-SEC-001 | TLS 전송 암호화 | FS-SEC-001 | §9.1 TLS 설정 | — | OQ-SEC-001 | |
| UR-SEC-002 | 비밀번호 BCrypt 해시 | FS-SEC-002 | §8.4, §4.2 password_hash | — | OQ-SEC-002 | |
| UR-SEC-003 | 세션 쿠키 보안 속성 | FS-SEC-003 | §9.2 쿠키 속성 | — | OQ-SEC-003 | |
| UR-SEC-004 | 입력 검증 (SQL Injection·XSS 방지) | FS-SEC-004 | §9.3 Spring Validation | — | OQ-SEC-004, OQ-SEC-005 | |
| UR-SEC-005 | 보안 HTTP 헤더 | FS-SEC-005 | §9.5 Nginx 헤더 | — | OQ-SEC-006 | |

### 2.14 백업 및 복구 (BKP)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | IQ/OQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-BKP-001 | 매일 DB 백업 | FS-BKP-001 | §10.3 pg_basebackup + WAL | RA-SYS-001 | IQ-BKP-001, IQ-BKP-002 | ● |
| UR-BKP-002 | MinIO Site Replication (DR) | FS-BKP-002 | §10.3 Site Replication | RA-SYS-002 | IQ-BKP-003 | |
| UR-BKP-003 | 반기 복구 훈련 (RTO 4h) | FS-BKP-003 | §10.3 DR 훈련 | RA-SYS-001 | PQ-BKP-001 | |
| UR-BKP-004 | 백업 무결성 검증 | FS-BKP-004 | §10.3 SHA-256 | — | IQ-BKP-004 | |

### 2.15 데이터 이관 (MIG)

| URS ID | 요구사항 요약 | FS 항목 | DS 섹션 | RA ID | PQ 케이스 | Critical |
|---|---|---|---|---|---|---|
| UR-MIG-001 | CLI 이관 도구 (edms-import) | FS-MIG-001 | §3.2 migration/ Spring Shell | RA-MIG-001 | PQ-MIG-001 | |
| UR-MIG-002 | metadata.csv 표준 형식 | FS-MIG-002 | §4.2 DDL 매핑 | RA-MIG-001 | PQ-MIG-002 | |
| UR-MIG-003 | 이관 파일 무결성 (SHA-256) | FS-MIG-003 | §5.4 MinIO 업로드, §8.5 | RA-MIG-002 | PQ-MIG-003 | |
| UR-MIG-004 | QA 이관 승인 필수 | FS-MIG-004 | §5.3 migration_approval 워크플로 | RA-MIG-003 | PQ-MIG-004, PQ-MIG-005 | |

### 2.16 운영 SOP 추적성 (OSOP)

운영 단계 SOP가 규제 요건 및 URS/FS/DS와 연결됨을 증명한다. 아래 각 SOP는 해당 조항을 충족하는 주요 통제 절차이다.

| SOP 번호 | SOP 제목 | 충족 규제 조항 | 연결 URS/FS/DS | 연결 컴플라이언스 매트릭스 |
|---|---|---|---|---|
| SOP-AUDIT-TRAIL-001 | 감사추적 검토 절차 | Part 11 §11.10(e); Annex 11 §9 | UR-AUD-001~007, FS-AUD-001~007, DS §8.2/8.3 | VAL-CM-001 §11.10(e); VAL-CM-002 §9 |
| SOP-DATA-INTEGRITY-001 | 데이터 무결성 관리 절차 | Part 11 §11.10(c)(e); Annex 11 §5/6 | UR-AUD-001~007, UR-SIG-001~008, DS §8.1/8.2 | VAL-CM-001 §11.10(c)(e); VAL-CM-002 §5 |
| SOP-DEVIATION-001 | 운영 일탈 관리 절차 | Annex 11 §13; ICH Q9 | UR-AUD-001 (ALCOA+), DS §9 (보안 통제) | VAL-CM-002 §13 |
| SOP-INCIDENT-001 | 보안사고 대응 절차 | Part 11 §11.10(d)(e)(j); Annex 11 §12/13 | UR-SEC-001~005, UR-AUD-001~003, DS §9.1~9.5 | VAL-CM-001 §11.10(d)(j); VAL-CM-002 §12/13 |
| SOP-TRAINING-001 | 사용자 교육·자격 절차 | Part 11 §11.10(i); Annex 11 §2 | UR-TRN-001~004, FS-TRN-001~004, DS §4.2 training_assignments | VAL-CM-001 §11.10(i); VAL-CM-002 §2 |

> **참고**: 기술 명세 문서(VAL-NET-001, VAL-SEC-001)와 CI/CD SOP(SOP-CICD-001)는 Annex 11 §3(공급사 관리), §10(변경관리)과 연계되며 컴플라이언스 매트릭스(VAL-CM-002)에서 증거로 인용된다.

---

## 3. 테스트 케이스 커버리지 요약

### 3.1 URS 요구사항별 커버리지

| 영역 | URS 요건 수 | OQ 연결 수 | PQ 연결 수 | IQ 연결 수 | 미연결 수 |
|---|---|---|---|---|---|
| AUTH | 6 | 13 | 0 | 0 | 0 |
| USER | 5 | 9 | 0 | 0 | 0 |
| ACC | 4 | 11 | 0 | 0 | 0 |
| DOC | 10 | 14 | 0 | 0 | 0 |
| LCY | 9 | 12 | 0 | 0 | 0 |
| SIG | 8 | 13 | 0 | 0 | 0 |
| AUD | 7 | 18 | 0 | 0 | 0 |
| SRCH | 5 | 6 | 0 | 0 | 0 |
| NTFY | 4 | 5 | 0 | 0 | 0 |
| TRN | 4 | 4 | 0 | 0 | 0 |
| ADMIN | 3 | 3 | 0 | 0 | 0 |
| PERF | 3 | 4 | 0 | 0 | 0 |
| SEC | 5 | 6 | 0 | 0 | 0 |
| BKP | 4 | 0 | 1 | 4 | 0 |
| MIG | 4 | 0 | 5 | 0 | 0 |
| **합계** | **78** | **118** | **6** | **4** | **0** |

**URS 커버리지: 78/78 (100%)** — 미연결 요구사항 없음.

### 3.2 Critical 기능 OQ 케이스 목록 (사전 정의)

| OQ 케이스 ID | 대상 기능 | 검증 내용 | 양성/음성 |
|---|---|---|---|
| OQ-AUTH-001 | 로그인 | 올바른 자격증명 → 로그인 성공 | 양성 |
| OQ-AUTH-002 | 로그인 | 잘못된 비밀번호 → 실패 및 감사로그 기록 | 음성 |
| OQ-AUTH-003 | 계정 잠금 | 5회 연속 실패 → 계정 LOCKED | 음성 |
| OQ-AUTH-004 | 계정 잠금 | 잠금 계정에서 올바른 PW 입력 → 거부 | 음성 |
| OQ-AUTH-005 | 잠금 해제 | 30분 후 자동 해제 확인 | 양성 |
| OQ-AUTH-012 | 단일 세션 | 두 번째 로그인 시 첫 번째 세션 만료 | 음성 |
| OQ-AUTH-013 | 단일 세션 | 만료된 세션 요청 → 로그인 화면 리다이렉트 | 음성 |
| OQ-LCY-002 | 상태 전이 | Draft → 검토중 (T-01, Author 제출) | 양성 |
| OQ-LCY-003 | 상태 전이 | 비Author가 제출 시도 → 403 거부 | 음성 |
| OQ-LCY-006 | 상태 전이 | min_signers 충족 → 시행중 전이 | 양성 |
| OQ-LCY-007 | qa_mandatory | QA 서명 없이 시행중 전이 시도 → 거부 | 음성 |
| OQ-LCY-016 | 상태 전이 | Draft에서 Effective 직행 시도 → 거부 | 음성 |
| OQ-SIG-001 | 전자서명 | 올바른 PW로 서명 → 서명 매니페스트 생성 | 양성 |
| OQ-SIG-002 | 전자서명 | 잘못된 PW로 서명 시도 → 거부 | 음성 |
| OQ-SIG-004 | 해시체인 | 서명 후 this_hash 연속성 검증 | 양성 |
| OQ-SIG-005 | INSERT-only | signature_manifests UPDATE 시도 → DB 거부 | 음성 |
| OQ-SIG-009 | 첫 서명 ID+PW | 세션 첫 서명 시 ID+PW 필수 확인 | 양성 |
| OQ-SIG-010 | 첫 서명 ID+PW | ID 없이 서명 시도 → 거부 | 음성 |
| OQ-AUD-001 | INSERT-only | audit_logs UPDATE 시도 → DB 거부 | 음성 |
| OQ-AUD-002 | INSERT-only | audit_logs DELETE 시도 → DB 거부 | 음성 |
| OQ-AUD-006 | WORM 앵커 | 일별 앵커 파일 MinIO 업로드 확인 | 양성 |
| OQ-AUD-007 | WORM 앵커 | 앵커 파일 삭제 시도 → MinIO 거부 | 음성 |
| OQ-AUD-008 | 감사로그 완결성 | 로그인 이벤트 → audit_logs 기록 확인 | 양성 |
| OQ-AUD-009 | 감사로그 완결성 | 문서 생성 이벤트 → audit_logs 기록 | 양성 |
| OQ-AUD-010 | 감사로그 완결성 | 상태 전이 이벤트 → audit_logs 기록 | 양성 |
| OQ-AUD-011 | 감사로그 완결성 | 전자서명 이벤트 → audit_logs 기록 | 양성 |
| OQ-AUD-012 | 감사로그 완결성 | 권한 변경 이벤트 → audit_logs 기록 | 양성 |
| OQ-ACC-001 | RBAC | 권한 없는 카테고리 문서 접근 시도 → 403 | 음성 |
| OQ-ACC-002 | RBAC | 권한 있는 카테고리 문서 접근 → 허용 | 양성 |
| OQ-ACC-005 | RBAC | API 직접 호출 (UI 우회) → 서버 권한 체크 | 음성 |
| OQ-DOC-002 | 채번 | SOP 문서 생성 → SOP-QC-001 형식 채번 | 양성 |
| OQ-DOC-003 | 채번 | 동시 생성 시 중복 채번 없음 확인 | 양성 |
| OQ-DOC-006 | PDF 변환 | 한글 DOCX → PDF 변환, 한글 깨짐 없음 | 양성 |

---

## 4. 역방향 추적성 (테스트 → URS)

역방향 추적성 확인: 아래 테스트 케이스가 모두 URS 요건에서 유래하였음을 확인한다.

| 테스트 범위 | 케이스 수 | URS 연결 완결성 |
|---|---|---|
| OQ 전체 케이스 | ~128개 (사전 정의 34개 + 추가 예정) | URS 요건에서 유래하지 않은 케이스 없음 |
| PQ 전체 케이스 | ~6개 (사전 정의) | URS 요건에서 유래하지 않은 케이스 없음 |
| IQ 체크리스트 | ~20개 항목 | UR-BKP 및 인프라 요건에서 유래 |

> OQ/PQ 프로토콜 작성 완료 후 역방향 추적성을 케이스 단위로 재검증한다.

---

## 5. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 초안 작성 | TBD |
| 0.2 | 2026-05-08 | §2.16 추가 — 운영 SOP 5종(AUDIT-TRAIL/DATA-INTEGRITY/DEVIATION/INCIDENT/TRAINING) 추적성 행 추가 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA 검토·승인 전까지 검증 활동의 기준으로 사용할 수 없다.*
