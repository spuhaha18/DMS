# Operational Qualification Protocol (OQ 프로토콜)
## Electronic Document Management System (EDMS)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-OQ-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토·승인 전 |
| 검증 단계 | OQ (Operational Qualification) |
| URS 참조 | VAL-URS-001 v0.3 |
| FS 참조 | VAL-FS-001 v0.1 |
| RA 참조 | VAL-RA-001 v0.1 |
| TM 참조 | VAL-TM-001 v0.1 |
| 전제 조건 | IQ (VAL-IQ-001) 합격 완료 |
| 작성자 | TBD |
| 검토자 | TBD |
| 승인자 | TBD (QA) |

---

## 목차

1. [목적 및 범위](#1-목적-및-범위)
2. [OQ 수행 전제조건](#2-oq-수행-전제조건)
3. [테스트 데이터 설계](#3-테스트-데이터-설계)
4. [OQ-AUTH: 인증 및 세션](#4-oq-auth-인증-및-세션)
5. [OQ-ACC: 접근 통제](#5-oq-acc-접근-통제)
6. [OQ-DOC: 문서 관리](#6-oq-doc-문서-관리)
7. [OQ-LCY: 문서 라이프사이클](#7-oq-lcy-문서-라이프사이클)
8. [OQ-SIG: 전자서명](#8-oq-sig-전자서명)
9. [OQ-AUD: 감사추적](#9-oq-aud-감사추적)
10. [OQ-SRCH: 검색](#10-oq-srch-검색)
11. [OQ-NTFY: 알림](#11-oq-ntfy-알림)
12. [OQ-PERF: 성능](#12-oq-perf-성능)
13. [OQ-SEC: 보안](#13-oq-sec-보안)
14. [OQ 요약 및 서명](#14-oq-요약-및-서명)
15. [이탈(Deviation) 기록](#15-이탈deviation-기록)
16. [변경 이력](#16-변경-이력)

---

## 1. 목적 및 범위

본 프로토콜은 EDMS가 FS(VAL-FS-001)에 정의된 기능 요구사항을 운영 환경에서 올바르게 수행하는지 검증한다. 특히 Risk Assessment(VAL-RA-001)에서 **Critical**로 분류된 기능을 최우선으로 검증한다.

각 테스트 케이스는 양성(Positive — 정상 동작) 또는 음성(Negative — 잘못된 입력·권한 거부) 시나리오로 구성되며, 예상 결과와 실제 결과를 대조하여 합격 여부를 판정한다.

**OQ 수행 환경**: 운영 환경 (Production) — OQ 전용 테스트 계정 사용, 테스트 완료 후 테스트 데이터 격리  
**OQ 수행자**: QA 또는 지정된 검증 담당자  
**IQ 선행 조건**: VAL-IQ-001 합격 서명 확인 후 OQ 착수

---

## 2. OQ 수행 전제조건

| # | 전제조건 | 확인 | 확인자 | 날짜 |
|---|---|---|---|---|
| PC-01 | IQ (VAL-IQ-001) 합격 서명 완료 | ☐ | | |
| PC-02 | OQ 프로토콜이 QA 승인 완료 상태 | ☐ | | |
| PC-03 | OQ 테스트 계정 생성 완료 (섹션 3 참조) | ☐ | | |
| PC-04 | OQ 테스트용 문서 샘플 파일 준비 완료 | ☐ | | |
| PC-05 | 감사로그 초기 상태 기록 완료 (audit_log 최신 ID) | ☐ | | |
| PC-06 | DB 직접 접속 도구(psql) 준비 완료 | ☐ | | |

---

## 3. 테스트 데이터 설계

### 3.1 OQ 테스트 계정

| 계정 ID | 역할 | 부서 | 용도 |
|---|---|---|---|
| oq-author-01 | Author | QC | 문서 작성·제출 |
| oq-reviewer-01 | Reviewer | QC | 검토 서명 |
| oq-approver-01 | Approver | QC | 승인 서명 |
| oq-qa-01 | QA | QA | QA 서명, 폐기, 이관 승인 |
| oq-reader-01 | Reader | QC | 문서 열람 전용 |
| oq-admin-01 | Admin | IT | 관리 기능 |
| oq-auditor-01 | Auditor | QA | 감사로그 조회 |
| oq-noperm-01 | Reader | R&D | QC 문서 권한 없음 (음성 테스트용) |

### 3.2 OQ 테스트용 워크플로 템플릿

**SOP 카테고리 워크플로**:
- Step 1: REVIEW — 역할: Reviewer, min_signers: 1, parallel: false
- Step 2: APPROVAL — 역할: Approver, min_signers: 1, parallel: false, qa_required: true

**Form 카테고리 워크플로**:
- Step 1: REVIEW — 역할: Reviewer, min_signers: 1
- Step 2: APPROVAL — 역할: Approver, min_signers: 1, qa_required: false

### 3.3 OQ 테스트용 채번 템플릿

- SOP: format=`{TYPE}-{DEPT}-{SEQ:3}`, scope=PER_DEPT

---

## 4. OQ-AUTH: 인증 및 세션

### OQ-AUTH-001: 정상 로그인

**FS 참조**: FS-AUTH-001 | **RA 참조**: RA-AUTH-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 올바른 자격증명으로 로그인 성공 확인 |
| **전제조건** | oq-author-01 계정 활성 상태 |
| **절차** | 1. 로그인 화면 접속<br>2. ID: `oq-author-01`, PW: (초기 비밀번호) 입력<br>3. [로그인] 클릭 |
| **예상 결과** | 대시보드로 이동, 세션 생성 확인 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUTH-002: 잘못된 자격증명 로그인 실패

**FS 참조**: FS-AUTH-001 (BR-AUTH-004) | **RA 참조**: RA-AUTH-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 잘못된 비밀번호 입력 시 로그인 거부 및 감사로그 기록 확인 |
| **절차** | 1. ID: `oq-author-01`, PW: `wrong_password` 입력<br>2. [로그인] 클릭 |
| **예상 결과** | 로그인 거부 오류 메시지 표시 ("잔여 시도 횟수: 4회"), audit_logs에 LOGIN_FAIL 기록 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUTH-003: 5회 연속 실패 → 계정 잠금

**FS 참조**: FS-AUTH-001 (BR-AUTH-004) | **RA 참조**: RA-AUTH-002 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 5회 연속 실패 시 계정 LOCKED 상태 전환 확인 |
| **전제조건** | oq-author-01의 failed_attempts = 0 초기화 |
| **절차** | 1. 잘못된 비밀번호로 5회 연속 로그인 시도<br>2. DB 확인: `SELECT status FROM users WHERE user_id = 'oq-author-01'` |
| **예상 결과** | 5번째 시도 후 "계정이 잠금 처리되었습니다" 메시지, DB status = `LOCKED` |
| **실제 결과** | |
| **DB 확인 결과** | status = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUTH-004: 잠금 계정에서 올바른 비밀번호 입력 → 거부

**FS 참조**: FS-AUTH-001 (BR-AUTH-004) | **RA 참조**: RA-AUTH-002 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 잠금된 계정은 올바른 비밀번호로도 로그인 불가 확인 |
| **전제조건** | oq-author-01이 LOCKED 상태 (OQ-AUTH-003 이후) |
| **절차** | 1. ID: `oq-author-01`, 올바른 비밀번호 입력<br>2. [로그인] 클릭 |
| **예상 결과** | "계정이 잠금 처리되었습니다" 오류 메시지, 로그인 거부 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUTH-009: 15분 비활성 세션 자동 만료

**FS 참조**: FS-AUTH-003 (BR-AUTH-014) | **RA 참조**: RA-AUTH-003

| 항목 | 내용 |
|---|---|
| **목적** | 15분 비활성 후 세션 자동 만료 확인 |
| **절차** | 1. oq-author-01으로 로그인<br>2. 16분 대기 (또는 서버에서 세션 TTL 조정하여 단축 테스트)<br>3. 아무 페이지 클릭 |
| **예상 결과** | 로그인 화면으로 리다이렉트 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUTH-012: 단일 세션 강제 — 두 번째 로그인 시 기존 세션 만료

**FS 참조**: FS-AUTH-003 (BR-AUTH-015) | **RA 참조**: RA-AUTH-004

| 항목 | 내용 |
|---|---|
| **목적** | 동일 사용자가 두 곳에서 동시에 로그인 불가 확인 |
| **절차** | 1. 브라우저 A에서 oq-author-01로 로그인<br>2. 브라우저 B에서 동일 계정으로 로그인<br>3. 브라우저 A에서 임의 페이지 클릭 |
| **예상 결과** | 브라우저 A에서 "다른 기기에서 로그인이 감지되었습니다" → 로그인 화면 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 5. OQ-ACC: 접근 통제

### OQ-ACC-001: 권한 없는 카테고리 문서 접근 거부

**FS 참조**: FS-ACC-001 | **RA 참조**: RA-ACC-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 권한 없는 사용자가 문서에 접근하지 못함을 확인 |
| **전제조건** | oq-noperm-01은 SOP 카테고리 권한 없음 |
| **절차** | 1. oq-noperm-01으로 로그인<br>2. 문서 목록 조회 (SOP 카테고리)<br>3. API 직접 호출: `GET /api/v1/documents?category=SOP` |
| **예상 결과** | SOP 문서가 목록에 표시되지 않음, API 직접 호출 시 403 Forbidden |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-ACC-005: UI 우회 API 직접 호출 권한 체크

**FS 참조**: FS-ACC-001 (BR-ACC-001) | **RA 참조**: RA-ACC-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 클라이언트 UI를 우회한 API 직접 호출에서도 서버 권한 검사가 작동함을 확인 |
| **절차** | 1. oq-noperm-01의 세션 쿠키 확보<br>2. curl로 직접 호출: `curl -b JSESSIONID=... https://edms.internal/api/v1/documents/1` |
| **예상 결과** | HTTP 403 Forbidden 응답 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-ACC-009: 다운로드 권한 없는 사용자의 PDF 다운로드 거부

**FS 참조**: FS-ACC-003 | **RA 참조**: RA-ACC-002

| 항목 | 내용 |
|---|---|
| **목적** | 다운로드 권한 없는 사용자가 PDF를 다운로드할 수 없음을 확인 |
| **전제조건** | oq-reader-01은 열람 권한만 있음 (can_download = false) |
| **절차** | 1. oq-reader-01으로 로그인<br>2. 시행중 SOP 문서 열람<br>3. 다운로드 버튼 UI 확인<br>4. API 직접 호출: `GET /api/v1/documents/1/versions/1/pdf/download` |
| **예상 결과** | UI에 다운로드 버튼 없음, API 호출 시 403 Forbidden |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 5A. OQ-USER: 사용자·역할·권한 관리

### OQ-USER-001: 신규 사용자 생성 — force_change_pw 강제

**FS 참조**: FS-USER-001 (BR-USER-003) | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | Admin이 신규 사용자 생성 시 force_change_pw=TRUE 자동 설정 확인 |
| **절차** | 1. oq-admin-01으로 로그인 → 2. POST /api/v1/admin/users (user_id=oq-temp-01, role=AUTHOR) → 3. DB 확인 |
| **예상 결과** | 응답 201, force_change_pw=true, 신규 사용자 첫 로그인 시 ForcePasswordChange 응답 |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-002: user_id 패턴 검증

**FS 참조**: FS-USER-001 (BR-USER-001) | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | user_id 패턴 `^[a-zA-Z0-9._-]{2,50}$` 위반 시 400 응답 확인 |
| **절차** | POST /api/v1/admin/users with user_id="bad id" (공백 포함) |
| **예상 결과** | 400 Bad Request, code=VALIDATION_001 |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-003: user_id 중복 거부

**FS 참조**: FS-USER-001 (BR-USER-001)

| 항목 | 내용 |
|---|---|
| **목적** | 기존 user_id로 POST 시 409 응답 확인 |
| **절차** | POST /api/v1/admin/users with user_id="admin" |
| **예상 결과** | 409 Conflict, code=USER_001 |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-004: 자기 자신 비활성화 거부 (BR-USER-010)

**FS 참조**: FS-USER-002 (BR-USER-010) | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | Admin이 자기 자신을 disable할 수 없음을 확인 |
| **절차** | oq-admin-01 로그인 후 POST /api/v1/admin/users/oq-admin-01/disable |
| **예상 결과** | 422 Unprocessable Entity, code=USER_005 |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-005: 사용자 비활성화 후 활성 세션 즉시 만료

**FS 참조**: FS-USER-002 (BR-USER-006) | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | DISABLED 전환 시 해당 사용자의 활성 세션이 즉시 만료됨을 확인 |
| **절차** | 1. oq-author-01 브라우저 A 로그인 → 2. oq-admin-01이 oq-author-01 disable → 3. 브라우저 A 페이지 클릭 |
| **예상 결과** | 브라우저 A → 401, 로그인 화면으로 |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-006: 역할 변경 즉시 적용 + 감사로그

**FS 참조**: FS-USER-003 (BR-USER-012) + FS-AUD-001

| 항목 | 내용 |
|---|---|
| **목적** | 역할 추가/제거가 즉시 적용되고 audit_logs에 ROLE_ASSIGNED/ROLE_REVOKED 기록 |
| **절차** | PUT /api/v1/admin/users/{pk}/roles with new role list |
| **예상 결과** | 200 OK + DB audit_logs에 두 종류 행 존재 |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-007: Auditor 유효기간 시작 전 로그인 거부 (BR-USER-014)

**FS 참조**: FS-USER-004 (BR-USER-014) | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | valid_from > today 인 계정 로그인 거부 확인 |
| **절차** | Auditor 계정 valid_from=tomorrow 설정 → 로그인 시도 |
| **예상 결과** | 401 AUTH_003 (Account is disabled) |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-008: Auditor 유효기간 종료 후 자동 비활성 (BR-USER-015)

**FS 참조**: FS-USER-004 (BR-USER-015) | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 일일 00:00 KST 스케줄러가 valid_until 만료 계정을 DISABLED로 전환 |
| **절차** | valid_until=yesterday 사용자 → AuditorExpirySessionScheduler 트리거 → DB 확인 |
| **예상 결과** | status=DISABLED, audit_logs에 AUDITOR_EXPIRED 기록 |
| **판정** | ☐ Pass ☐ Fail |

### OQ-USER-009: 권한 매트릭스 3D 조회 — Role × Category × Department

**FS 참조**: FS-ACC-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | (role, category, department) 3D 매칭 + NULL department 매칭 동작 확인 |
| **절차** | 1. AUTHOR/SOP/QC 권한 upsert → GET /api/v1/admin/permissions?role_id=&category_id= → 2. AUTHOR/SOP/NULL 권한 upsert → 동일 GET |
| **예상 결과** | 두 행이 별도 PK로 존재; 동일한 (role,cat,NULL) upsert 시 PK 동일 (UNIQUE NULLS NOT DISTINCT) |
| **판정** | ☐ Pass ☐ Fail |

---

## 6. OQ-DOC: 문서 관리

### OQ-DOC-001: 시드 카테고리 존재 검증

**FS 참조**: FS-DOC-001 | **RA 참조**: RA-DOC-003 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | V12 적용 후 SOP/METHOD/SPEC/FORM 4개 카테고리가 활성 상태로 존재함을 확인 |
| **전제조건** | M3 마이그레이션 완료 (V9~V12) |
| **절차** | 1. admin 계정으로 로그인<br>2. `GET /api/v1/admin/categories` 호출<br>3. 응답에서 SOP, METHOD, SPEC, FORM 카테고리 모두 `is_active=true` 확인<br>4. 각 카테고리에 대해 `GET /api/v1/admin/numbering-templates` 조회 → 4개 템플릿 확인 |
| **예상 결과** | 4개 카테고리 모두 `active=true`, `numbering_templates` 4개 매핑됨 |
| **실제 결과** | 카테고리 수 = , 활성 수 = , 템플릿 수 = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |
| **자동화** | `M3SmokeIT.시드_카테고리_4개_존재()` |

---

### OQ-DOC-002: 채번 — SOP 문서 자동 채번

**FS 참조**: FS-DOC-001 | **RA 참조**: RA-DOC-003

| 항목 | 내용 |
|---|---|
| **목적** | SOP 문서 생성 시 채번 템플릿에 따라 문서 번호가 자동으로 생성됨을 확인 |
| **전제조건** | SOP 채번 템플릿: `{TYPE}-{DEPT}-{SEQ:3}`, scope=PER_DEPT |
| **절차** | 1. oq-author-01으로 로그인<br>2. 신규 SOP 문서 생성 (부서: QC)<br>3. 생성된 문서 번호 확인 |
| **예상 결과** | 문서 번호 = `SOP-QC-001` (첫 번째 생성 시) |
| **실제 결과** | 문서 번호 = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-DOC-003: 채번 — 중복 번호 없음 확인

**FS 참조**: FS-DOC-001 | **RA 참조**: RA-DOC-003

| 항목 | 내용 |
|---|---|
| **목적** | 동시에 문서를 생성하더라도 중복 채번이 발생하지 않음을 확인 |
| **절차** | 1. SOP QC 문서를 연속으로 3개 생성<br>2. 각 문서 번호 확인 |
| **예상 결과** | SOP-QC-002, SOP-QC-003, SOP-QC-004 (순차 채번, 중복 없음) |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-DOC-004: 채번 — 부서 독립 시퀀스

**FS 참조**: FS-DOC-001 | **RA 참조**: RA-DOC-003 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | PER_DEPT 채번에서 다른 부서는 독립 시퀀스로 시작함을 확인 |
| **전제조건** | SOP 채번 템플릿이 `PER_DEPT` scope로 설정됨 |
| **절차** | 1. QC 부서에 SOP 문서 생성 → 번호 `SOP-QC-001` 확인<br>2. QA 부서에 SOP 문서 생성 → 번호 `SOP-QA-001` 확인 (QC와 독립)<br>3. QC 부서에 SOP 문서 다시 생성 → 번호 `SOP-QC-002` 확인 |
| **예상 결과** | QC와 QA 시퀀스가 서로 독립적; QC는 001→002, QA는 독자적으로 001 시작 |
| **실제 결과** | QC 1차 = , QA 1차 = , QC 2차 = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |
| **자동화** | `M3SmokeIT.부서별_독립_시퀀스()` |

---

### OQ-DOC-005: 파일 업로드 — 허용 형식 + 거부 형식

**FS 참조**: FS-DOC-002 (BR-DOC-005) | **RA 참조**: RA-DOC-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | BR-DOC-005 허용 파일 형식 화이트리스트 검증 |
| **전제조건** | 초안 상태 SOP 문서 존재, oq-author-01 권한 보유 |
| **절차** | 1. `.docx` 파일 업로드 → **201 Created**<br>2. `.xlsx` 파일 업로드 → **201 Created**<br>3. `.pptx` 파일 업로드 → **201 Created**<br>4. `.pdf` 파일 업로드 → **201 Created**<br>5. `.txt` 파일 업로드 시도 → **415 Unsupported Media Type**<br>6. `.exe`를 `.docx`로 이름 변경 후 업로드 → **422** (magic bytes 불일치) |
| **예상 결과** | 4개 허용 파일 형식 → 201; `.txt` → 415; magic bytes 불일치 → 422 |
| **실제 결과** | .docx= , .xlsx= , .pptx= , .pdf= , .txt= , fake .docx= |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |
| **자동화** | `FileTypeValidatorTest` (단위), `DocumentFileControllerIT.파일형식_거부()` |

---

### OQ-DOC-006: 한글 DOCX → PDF 변환 품질

**FS 참조**: FS-DOC-003 | **RA 참조**: RA-DOC-002 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 한글 텍스트를 포함한 DOCX 파일이 PDF로 올바르게 변환됨을 확인 |
| **전제조건** | 한글 텍스트·표·그림 포함 테스트 DOCX 파일 준비 |
| **절차** | 1. oq-author-01으로 로그인<br>2. 신규 SOP 문서 생성<br>3. 한글 DOCX 파일 업로드<br>4. PDF 변환 완료 대기 (최대 5분)<br>5. pdf.js 뷰어에서 PDF 열람<br>6. 한글 텍스트 정상 표시 여부 확인 |
| **예상 결과** | PDF 변환 완료, 한글 텍스트 깨짐 없이 표시, 표·그림 레이아웃 유지 |
| **실제 결과** | |
| **변환 소요 시간** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 7. OQ-LCY: 문서 라이프사이클

### OQ-LCY-002: T-01 상태 전이 — 초안 → 검토중

**FS 참조**: FS-LCY-001 (T-01) | **RA 참조**: RA-LCY-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | Author가 초안 문서를 제출하면 검토중으로 전이됨을 확인 |
| **전제조건** | oq-author-01이 초안 SOP 문서 보유 |
| **절차** | 1. oq-author-01으로 로그인<br>2. 초안 문서에서 [검토 요청] 클릭<br>3. 문서 상태 확인 |
| **예상 결과** | 상태 = 검토중, 워크플로 인스턴스 생성됨, Reviewer에게 알림 발송 |
| **실제 결과** | 상태 = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-LCY-003: 비Author의 검토 요청 시도 → 거부

**FS 참조**: FS-LCY-001 | **RA 참조**: RA-LCY-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | Author 권한이 없는 사용자가 검토 요청을 할 수 없음을 확인 |
| **절차** | 1. oq-reader-01으로 로그인<br>2. 초안 문서에서 API 직접 호출: `POST /api/v1/documents/1/versions/1/submit` |
| **예상 결과** | HTTP 403 Forbidden |
| **실제 결과** | HTTP 응답 코드 = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-LCY-006: T-03 전이 — 승인중 → 시행중 (QA 서명 포함)

**FS 참조**: FS-LCY-001 (T-03), FS-LCY-005 | **RA 참조**: RA-LCY-001, RA-LCY-003 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 모든 승인 서명(QA 포함) 완료 시 시행중으로 전이됨을 확인 |
| **전제조건** | oq-approver-01이 승인 서명 완료, oq-qa-01이 QA 서명 완료 |
| **절차** | 1. oq-approver-01으로 승인 서명<br>2. oq-qa-01으로 QA 서명<br>3. 문서 상태 확인<br>4. revision 번호 확인 (0이어야 함) |
| **예상 결과** | 상태 = 시행중, revision = 0, effective_date = 오늘 날짜 |
| **실제 결과** | 상태 = , revision = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-LCY-007: QA 서명 없이 시행중 전이 시도 → 거부

**FS 참조**: FS-LCY-005 | **RA 참조**: RA-LCY-003 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | qa_mandatory=true인 카테고리에서 QA 서명 없이 시행중 전이가 거부됨을 확인 |
| **전제조건** | Approver 서명만 완료, QA 서명 미완료 상태 |
| **절차** | 1. oq-qa-01이 QA 서명하지 않은 상태에서<br>2. API 직접 호출로 시행중 전이 시도 |
| **예상 결과** | HTTP 400 또는 422, "QA 서명이 필요합니다" 오류 |
| **실제 결과** | HTTP 응답 = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-LCY-016: 직접 전이 시도 — 초안 → 시행중 거부

**FS 참조**: FS-LCY-001 | **RA 참조**: RA-LCY-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 허가되지 않은 상태 전이(초안에서 시행중 직행)가 시스템에서 거부됨을 확인 |
| **절차** | 1. 초안 상태 문서 버전 ID 확인<br>2. API 직접 호출하여 시행중으로 전이 시도 (비정상 경로) |
| **예상 결과** | HTTP 400 "허용되지 않은 상태 전이입니다" |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 8. OQ-SIG: 전자서명

### OQ-SIG-001: 올바른 비밀번호로 서명 성공

**FS 참조**: FS-SIG-001, FS-SIG-004 | **RA 참조**: RA-SIG-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 올바른 비밀번호로 전자서명이 생성됨을 확인 |
| **전제조건** | 검토중 상태 문서의 워크플로에서 oq-reviewer-01이 검토 담당자로 배정됨 |
| **절차** | 1. oq-reviewer-01으로 로그인<br>2. 할당된 검토 문서 클릭<br>3. [서명] 버튼 클릭<br>4. 서명 의미: REVIEWED 선택<br>5. 올바른 비밀번호 입력<br>6. [서명 확인] 클릭 |
| **예상 결과** | 서명 성공, signature_manifests에 레코드 생성, this_hash 값 존재 |
| **실제 결과** | 서명 ID = , this_hash = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-SIG-002: 잘못된 비밀번호로 서명 시도 → 거부

**FS 참조**: FS-SIG-004 | **RA 참조**: RA-SIG-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 잘못된 비밀번호로 전자서명이 생성되지 않음을 확인 |
| **절차** | 1. oq-reviewer-01으로 로그인<br>2. 서명 다이얼로그에서 잘못된 비밀번호 입력<br>3. [서명 확인] 클릭 |
| **예상 결과** | "비밀번호가 올바르지 않습니다" 오류, 서명 레코드 생성 없음 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-SIG-004: 서명 해시체인 연속성 검증

**FS 참조**: FS-SIG-002 | **RA 참조**: RA-SIG-002, RA-SIG-003 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 연속된 서명들의 해시체인이 올바르게 연결되어 있음을 확인 |
| **절차** | 1. 3개의 서명 레코드 생성 (OQ-SIG-001 포함)<br>2. DB 조회: `SELECT id, prev_hash, this_hash FROM signature_manifests ORDER BY id`<br>3. 체인 검증: 각 레코드의 prev_hash가 이전 레코드의 this_hash와 일치하는지 확인 |
| **예상 결과** | 레코드 N의 prev_hash = 레코드 N-1의 this_hash |
| **실제 결과** | |
| **체인 연속성 확인** | ☐ 연속 ☐ 단절 |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-SIG-005: signature_manifests UPDATE 시도 → DB 거부

**FS 참조**: FS-SIG-002 | **RA 참조**: RA-SIG-002 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | app_role로 서명 레코드를 수정할 수 없음을 DB 수준에서 확인 |
| **절차** | 1. psql에서 app_role 세션으로 접속<br>2. `SET ROLE app_role;`<br>3. `UPDATE signature_manifests SET meaning = 'TAMPERED' WHERE id = 1;` 실행 |
| **예상 결과** | `ERROR: permission denied for table signature_manifests` |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-SIG-009: 세션 첫 서명 시 ID+PW 필수

**FS 참조**: FS-SIG-003 | **RA 참조**: RA-SIG-006 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | Part 11 §11.200(a) — 세션 내 첫 번째 서명은 ID+PW 모두 필요함을 확인 |
| **절차** | 1. 새 세션으로 oq-reviewer-01 로그인<br>2. 서명 다이얼로그에서 ID 필드 존재 여부 확인<br>3. ID 없이 PW만 입력 후 서명 시도 |
| **예상 결과** | 첫 서명 시 ID 필드가 표시됨, ID 없이 시도 시 거부 |
| **실제 결과** | ID 필드 표시 = ☐ Yes ☐ No |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 9. OQ-AUD: 감사추적

### OQ-AUD-001: audit_logs UPDATE 시도 → DB 거부

**FS 참조**: FS-AUD-002 | **RA 참조**: RA-AUD-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | app_role로 감사로그를 수정할 수 없음을 DB 수준에서 확인 |
| **절차** | 1. psql에서 app_role 세션으로 접속<br>2. `SET ROLE app_role;`<br>3. `UPDATE audit_logs SET action = 'TAMPERED' WHERE id = 1;` 실행 |
| **예상 결과** | `ERROR: permission denied for table audit_logs` |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUD-002: audit_logs DELETE 시도 → DB 거부

**FS 참조**: FS-AUD-002 | **RA 참조**: RA-AUD-001 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | app_role로 감사로그를 삭제할 수 없음을 DB 수준에서 확인 |
| **절차** | `DELETE FROM audit_logs WHERE id = 1;` (app_role 세션에서 실행) |
| **예상 결과** | `ERROR: permission denied for table audit_logs` |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUD-006: 일별 WORM 앵커 생성 확인

**FS 참조**: FS-AUD-003 | **RA 참조**: RA-AUD-003 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | 일별 감사 앵커 파일이 MinIO에 업로드되고 audit_checkpoints에 기록됨을 확인 |
| **절차** | 1. 앵커링 스케줄러 수동 트리거 (또는 다음날 01:00 이후 확인)<br>2. MinIO 확인: `mc ls edms-audit-anchors/anchors/YYYY/MM/`<br>3. DB 확인: `SELECT * FROM audit_checkpoints ORDER BY checkpoint_date DESC LIMIT 1;` |
| **예상 결과** | MinIO에 앵커 파일 존재, audit_checkpoints에 오늘 날짜 레코드 존재 |
| **실제 결과** | MinIO 파일 존재 = ☐ Yes ☐ No, DB 레코드 = |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUD-007: WORM 앵커 파일 삭제 시도 → MinIO 거부

**FS 참조**: FS-AUD-003, FS-AUD-007 | **RA 참조**: RA-AUD-003 | **Critical**: ●

| 항목 | 내용 |
|---|---|
| **목적** | COMPLIANCE Object Lock으로 보호된 앵커 파일이 삭제되지 않음을 확인 |
| **전제조건** | OQ-AUD-006에서 앵커 파일 생성 완료 |
| **절차** | `mc rm edms-audit-anchors/anchors/YYYY/MM/YYYYMMDD.json` 실행 |
| **예상 결과** | `AccessDenied: Object Locked` 오류 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-AUD-008~015: 감사로그 완결성 — 주요 이벤트별 기록 확인

**FS 참조**: FS-AUD-001 | **RA 참조**: RA-AUD-004 | **Critical**: ●

각 이벤트 발생 후 audit_logs에서 해당 레코드를 조회하여 기록 여부를 확인한다.

**검증 쿼리**: `SELECT * FROM audit_logs WHERE action = '<ACTION>' ORDER BY server_ts DESC LIMIT 1;`

| 케이스 ID | 이벤트 | action 코드 | 발생 절차 | 기록 확인 | 판정 |
|---|---|---|---|---|---|
| OQ-AUD-008 | 로그인 성공 | `LOGIN_SUCCESS` | oq-author-01 로그인 | ☐ | ☐ Pass ☐ Fail |
| OQ-AUD-009 | 로그인 실패 | `LOGIN_FAIL` | 잘못된 PW 입력 | ☐ | ☐ Pass ☐ Fail |
| OQ-AUD-010 | 문서 생성 | `DOCUMENT_CREATE` | 신규 문서 생성 | ☐ | ☐ Pass ☐ Fail |
| OQ-AUD-011 | 상태 전이 | `STATE_TRANSITION` | 검토 요청 제출 | ☐ | ☐ Pass ☐ Fail |
| OQ-AUD-012 | 전자서명 | `SIGNATURE_CREATED` | 검토 서명 | ☐ | ☐ Pass ☐ Fail |
| OQ-AUD-013 | 권한 변경 | `PERMISSION_UPDATED` | 권한 매트릭스 수정 | ☐ | ☐ Pass ☐ Fail |
| OQ-AUD-014 | 계정 생성 | `USER_CREATED` | 신규 사용자 생성 | ☐ | ☐ Pass ☐ Fail |
| OQ-AUD-015 | 로그아웃 | `LOGOUT` | 명시적 로그아웃 | ☐ | ☐ Pass ☐ Fail |

---

## 10. OQ-SRCH: 검색

### OQ-SRCH-001: 제목 키워드 검색

**FS 참조**: FS-SRCH-001

| 항목 | 내용 |
|---|---|
| **목적** | 문서 제목 키워드로 검색 결과가 반환됨을 확인 |
| **전제조건** | "원료 수탁시험 절차" 제목의 시행중 SOP 문서 존재 |
| **절차** | 1. oq-author-01으로 로그인<br>2. 검색창에 "수탁시험" 입력<br>3. 검색 실행 |
| **예상 결과** | 해당 문서가 검색 결과에 포함됨 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-SRCH-005: 검색 결과 권한 필터

**FS 참조**: FS-SRCH-004

| 항목 | 내용 |
|---|---|
| **목적** | 권한 없는 문서가 검색 결과에 노출되지 않음을 확인 |
| **전제조건** | oq-noperm-01은 SOP 카테고리 권한 없음 |
| **절차** | 1. oq-noperm-01으로 로그인<br>2. "수탁시험" 검색 |
| **예상 결과** | SOP 문서가 검색 결과에 표시되지 않음 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 11. OQ-NTFY: 알림

### OQ-NTFY-001: 이메일 알림 발송 확인

**FS 참조**: FS-NTFY-001

| 항목 | 내용 |
|---|---|
| **목적** | 워크플로 이벤트 발생 시 이메일 알림이 발송됨을 확인 |
| **절차** | 1. oq-author-01이 문서 검토 요청 제출<br>2. oq-reviewer-01의 이메일 수신 확인 |
| **예상 결과** | oq-reviewer-01 이메일에 검토 요청 알림 수신 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-NTFY-002: SSE 실시간 알림 수신

**FS 참조**: FS-NTFY-002

| 항목 | 내용 |
|---|---|
| **목적** | SSE를 통한 실시간 앱 내 알림이 즉시 수신됨을 확인 |
| **절차** | 1. oq-reviewer-01이 로그인된 상태에서 알림 아이콘 확인<br>2. 다른 세션에서 oq-author-01이 검토 요청 제출<br>3. oq-reviewer-01 화면에서 알림 아이콘 변화 확인 (새로고침 없이) |
| **예상 결과** | 알림 아이콘에 새 알림 표시 (페이지 새로고침 없이 실시간) |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 12. OQ-PERF: 성능

### OQ-PERF-001: 문서 목록 조회 응답 시간

**FS 참조**: FS-PERF-001

| 항목 | 내용 |
|---|---|
| **목적** | 문서 목록 API 응답 시간이 2초 이내임을 확인 |
| **전제조건** | DB에 문서 100건 이상 존재 |
| **절차** | `GET /api/v1/documents?page=0&size=20` 요청 10회 실행, 평균 응답 시간 측정 |
| **예상 결과** | 평균 응답 시간 < 2,000ms |
| **실제 결과** | 평균 응답 시간 = ms |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-PERF-004: PDF 변환 시간

**FS 참조**: FS-PERF-003

| 항목 | 내용 |
|---|---|
| **목적** | 5MB 이하 DOCX 파일의 PDF 변환이 5분 이내에 완료됨을 확인 |
| **절차** | 1. 5MB DOCX 파일 업로드<br>2. 업로드 시각 기록<br>3. pdf_status = DONE 전환 시각 기록<br>4. 소요 시간 계산 |
| **예상 결과** | 변환 소요 시간 < 300초 |
| **실제 결과** | 소요 시간 = 초 |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 13. OQ-SEC: 보안

### OQ-SEC-001: TLS 프로토콜 확인

**FS 참조**: FS-SEC-001

| 항목 | 내용 |
|---|---|
| **목적** | 시스템이 TLS 1.2 이상으로만 통신함을 확인 |
| **절차** | `openssl s_client -connect edms.internal:443 -tls1_1` 실행 (TLS 1.1 시도) |
| **예상 결과** | TLS 1.1 연결 실패 (`handshake failure`) |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

### OQ-SEC-004: SQL Injection 방어

**FS 참조**: FS-SEC-004

| 항목 | 내용 |
|---|---|
| **목적** | SQL Injection 입력이 시스템에서 무해하게 처리됨을 확인 |
| **절차** | 1. 검색창에 `'; DROP TABLE documents; --` 입력<br>2. 검색 실행<br>3. documents 테이블 확인 |
| **예상 결과** | 입력이 검색어로 처리되거나 오류 반환, documents 테이블 정상 유지 |
| **실제 결과** | |
| **판정** | ☐ Pass ☐ Fail |
| **수행자** | |
| **날짜** | |

---

## 14. OQ 요약 및 서명

### 14.1 OQ 테스트 결과 집계

| 섹션 | 테스트 케이스 수 | Pass | Fail | 이탈 |
|---|---|---|---|---|
| OQ-USER (사용자·역할·권한) | 9 | | | |
| OQ-AUTH (인증) | 13 | | | |
| OQ-ACC (접근통제) | 11 | | | |
| OQ-DOC (문서관리) | 17 | | | |
| OQ-LCY (라이프사이클) | 12 | | | |
| OQ-SIG (전자서명) | 13 | | | |
| OQ-AUD (감사추적) | 18 | | | |
| OQ-SRCH (검색) | 6 | | | |
| OQ-NTFY (알림) | 5 | | | |
| OQ-PERF (성능) | 4 | | | |
| OQ-SEC (보안) | 6 | | | |
| **합계** | **114** | | | |

> 본 프로토콜 내 케이스는 대표 케이스이며, 실제 OQ 수행 시 세부 케이스가 추가될 수 있다.

### 14.2 OQ 합격 기준

- 모든 Critical 케이스 Pass: **필수**
- Non-Critical 케이스 Fail 시: Deviation 기록 + 영향 평가 + 시정 조치
- Critical 케이스 Fail 시: OQ 불합격 → 시정 후 재검증 → OQ 재수행

### 14.3 OQ 결론

```
☐ OQ 합격 — PQ 진행 승인
☐ 조건부 합격 — Deviation 해결 후 PQ 진행 (세부 내용: 이탈 기록 섹션 참조)
☐ OQ 불합격 — Critical 케이스 Fail → 시정 조치 후 재수행
```

### 14.4 서명란

| 역할 | 이름 | 서명 | 날짜 |
|---|---|---|---|
| 수행자 (QA/검증 담당) | | | |
| 검토자 (개발 책임자) | | | |
| 승인자 (QA Manager) | | | |

---

## 15. 이탈(Deviation) 기록

| 이탈 번호 | 케이스 ID | 이탈 내용 | 영향 평가 | 시정 조치 | 완료 날짜 | 담당자 |
|---|---|---|---|---|---|---|
| DEV-OQ-001 | | | | | | |

이탈 없음 시: "이탈 없음 (No Deviations)" 기록.

---

## 16. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 초안 작성 | TBD |
| 0.2 | 2026-05-10 | M3 OQ-DOC-001/004/005 추가 (시드 카테고리, 부서별 독립 시퀀스, 파일 형식 검증) | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA 승인 전까지 검증 수행에 사용할 수 없다.*
