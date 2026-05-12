# Functional Specification (FS)
## Electronic Document Management System (EDMS)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-FS-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| URS 참조 | VAL-URS-001 v0.3 |
| 작성자 | TBD |
| 검토자 | TBD |
| 승인자 | TBD (QA) |

---

## 목차

1. [목적 및 범위](#1-목적-및-범위)
2. [참조 문서](#2-참조-문서)
3. [시스템 기능 개요](#3-시스템-기능-개요)
4. [FS-AUTH: 인증 및 세션](#4-fs-auth-인증-및-세션)
5. [FS-USER: 사용자 및 역할 관리](#5-fs-user-사용자-및-역할-관리)
6. [FS-ACC: 접근 통제](#6-fs-acc-접근-통제)
7. [FS-DOC: 문서 관리](#7-fs-doc-문서-관리)
8. [FS-LCY: 문서 라이프사이클](#8-fs-lcy-문서-라이프사이클)
9. [FS-SIG: 전자서명](#9-fs-sig-전자서명)
10. [FS-AUD: 감사추적](#10-fs-aud-감사추적)
11. [FS-SRCH: 검색 및 조회](#11-fs-srch-검색-및-조회)
12. [FS-NTFY: 알림 및 정기검토](#12-fs-ntfy-알림-및-정기검토)
13. [FS-TRN: 교육 및 배포](#13-fs-trn-교육-및-배포)
14. [FS-ADMIN: 시스템 관리](#14-fs-admin-시스템-관리)
15. [FS-PERF: 성능 및 가용성](#15-fs-perf-성능-및-가용성)
16. [FS-SEC: 보안](#16-fs-sec-보안)
17. [FS-BKP: 백업 및 복구](#17-fs-bkp-백업-및-복구)
18. [FS-MIG: 데이터 이관](#18-fs-mig-데이터-이관)
19. [URS-FS 추적성 매트릭스](#19-urs-fs-추적성-매트릭스)
20. [변경 이력](#20-변경-이력)

---

## 1. 목적 및 범위

본 문서는 VAL-URS-001에 정의된 사용자 요구사항을 시스템이 어떻게 충족하는지를 기능적 관점에서 기술한다. 구현 기술(언어, 프레임워크, DB 구조)은 설계명세(DS)에서 다루며, 본 FS는 시스템의 동작 방식과 비즈니스 규칙을 정의한다.

본 FS는 이후 작성될 OQ(운영적격성) 프로토콜의 직접적인 입력이 되며, 각 FS 항목은 하나 이상의 OQ 테스트 케이스로 추적되어야 한다.

**범위**: Phase 1.a — SOP, Method, Specification, Form 문서 카테고리 대상.

---

## 2. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-URS-001 v0.3 | User Requirements Specification |
| VAL-VP-001 | Validation Plan |
| 21 CFR Part 11 | Electronic Records; Electronic Signatures |
| EU GMP Annex 11 | Computerised Systems |
| GAMP 5 (2nd Ed.) | Risk-Based Approach to GxP Computerized Systems |

---

## 3. 시스템 기능 개요

### 3.1 기능 모듈 구성

```
EDMS
├── 인증·세션 모듈        — 로그인, 비밀번호 관리, 세션 제어
├── 사용자·역할 모듈      — 계정 CRUD, 역할 정의, 배정
├── 접근통제 모듈         — 3차원 RBAC, 문서별 ACL
├── 문서 모듈             — 채번, 업로드, PDF 변환, 워터마크, 열람
├── 라이프사이클 모듈     — 상태머신, 워크플로 템플릿, 워크플로 인스턴스
├── 전자서명 모듈         — 서명 다이얼로그, 재인증, 매니페스트, 해시체인
├── 감사추적 모듈         — 자동 로깅, 해시체인, WORM 앵커링
├── 검색 모듈             — 메타데이터 검색, 전문 검색
├── 알림·정기검토 모듈    — 이메일, 앱 내 알림센터, 재검토 스케줄러
├── 교육 모듈             — 교육 과제, 열람 확인 서명
├── 관리 모듈             — 마스터데이터, 채번 템플릿, 워크플로 템플릿
├── 데이터 이관 모듈      — 벌크 임포트, QA 마이그레이션 승인
└── 인프라 모듈           — 백업, DR, WORM 스토리지
```

### 3.2 상태 코드 정의

시스템 전반에서 사용하는 문서 상태 코드:

| 상태명 | 내부 코드 | 설명 |
|---|---|---|
| 초안 | `DRAFT` | 작성 중 |
| 검토중 | `UNDER_REVIEW` | 검토 단계 진행 중 |
| 승인중 | `UNDER_APPROVAL` | 승인 단계 진행 중 |
| 시행중 | `EFFECTIVE` | 현행 유효 문서 |
| 개정중 | `UNDER_REVISION` | 기존 시행중 유지, 신규 초안 병행 |
| 대체됨 | `SUPERSEDED` | 신 버전으로 대체됨, 아카이브 |
| 폐기됨 | `RETIRED` | 사용 중단, 영구 보존 |

---

## 4. FS-AUTH: 인증 및 세션

### FS-AUTH-001: 로그인 처리
**URS 참조**: UR-AUTH-001, UR-AUTH-002, UR-AUTH-003

**기능 설명**:
사용자는 로그인 화면에서 사용자 ID와 비밀번호를 입력하여 시스템에 접근한다. 시스템은 입력된 자격증명을 검증하고, 성공 시 세션을 생성한 후 대시보드로 이동시킨다.

**비즈니스 규칙**:
- BR-AUTH-001: 사용자 ID와 비밀번호는 모두 필수 입력이다.
- BR-AUTH-002: 비밀번호는 단방향 해시로 저장되며, 검증 시 입력값을 해시하여 저장값과 비교한다.
- BR-AUTH-003: 로그인 실패 횟수는 사용자 계정별로 관리한다. 실패 횟수는 로그인 성공 또는 관리자 초기화 시 0으로 리셋된다.
- BR-AUTH-004: 연속 로그인 실패 5회 시 계정이 잠금(`LOCKED`) 상태로 전환된다. 잠금된 계정은 올바른 비밀번호를 입력해도 로그인이 거부된다.
- BR-AUTH-005: 계정 잠금은 Admin이 수동 해제하거나, 마지막 실패 시점으로부터 30분 경과 시 자동 해제된다.
- BR-AUTH-006: 비활성화(`DISABLED`) 계정은 로그인이 거부된다.
- BR-AUTH-007: 유효기간이 설정된 계정(Auditor)은 종료일 이후 로그인이 거부된다.

**화면 구성**:
- 사용자 ID 입력 필드 (자동완성 비활성화)
- 비밀번호 입력 필드 (마스킹, 자동완성 비활성화)
- [로그인] 버튼
- 실패 시 오류 메시지 표시 영역 (잔여 허용 횟수 표시)

**입력**: 사용자 ID (문자열), 비밀번호 (문자열)

**출력**: 세션 토큰 (서버 관리), 대시보드 화면 리다이렉트

**오류 처리**:
- 자격증명 불일치: "사용자 ID 또는 비밀번호가 올바르지 않습니다. 잔여 시도 횟수: N회"
- 계정 잠금: "계정이 잠금 처리되었습니다. 관리자에게 문의하거나 30분 후 재시도하십시오."
- 계정 비활성화: "비활성화된 계정입니다. 관리자에게 문의하십시오."
- 유효기간 만료: "계정 유효기간이 만료되었습니다."

**감사로그**: 로그인 성공·실패·잠금 이벤트 모두 기록 (사용자 ID, IP, 결과, 타임스탬프)

---

### FS-AUTH-002: 비밀번호 정책 적용
**URS 참조**: UR-AUTH-002

**기능 설명**:
비밀번호 설정·변경 시 정책을 검증하고, 위반 시 설정을 거부한다.

**비즈니스 규칙**:
- BR-AUTH-008: 비밀번호 최소 길이: 8자.
- BR-AUTH-009: 비밀번호는 대문자, 소문자, 숫자, 특수문자(`!@#$%^&*()` 등) 중 3종 이상을 포함해야 한다.
- BR-AUTH-010: 최근 5개 비밀번호와 동일한 값은 설정 불가. 시스템은 최근 5개 비밀번호 해시를 보관한다.
- BR-AUTH-011: Admin이 사용자 비밀번호를 초기화하면, 해당 사용자는 다음 로그인 시 반드시 비밀번호를 변경해야 한다(`FORCE_CHANGE` 플래그).
- BR-AUTH-012: 최초 생성된 계정은 초기 로그인 시 비밀번호 변경이 강제된다.

**오류 처리**:
- 길이 미충족: "비밀번호는 8자 이상이어야 합니다."
- 복잡도 미충족: "대문자, 소문자, 숫자, 특수문자 중 3종 이상을 포함해야 합니다."
- 이전 비밀번호 재사용: "최근 사용한 비밀번호는 사용할 수 없습니다."

---

### FS-AUTH-003: 세션 관리
**URS 참조**: UR-AUTH-004, UR-AUTH-006

**기능 설명**:
로그인 성공 시 서버에서 세션을 생성하고, 비활성 또는 복수 세션 조건에서 세션을 종료한다.

**비즈니스 규칙**:
- BR-AUTH-013: 세션은 서버에서 관리하며, 세션 ID는 추측 불가능한 난수로 생성된다.
- BR-AUTH-014: 마지막 요청으로부터 15분 경과 시 세션이 자동 만료된다. 만료된 세션에서의 요청은 로그인 화면으로 리다이렉트된다.
- BR-AUTH-015: 동일 사용자 ID로 두 번째 로그인 시도 시, 기존 세션이 즉시 만료된다. 기존 세션에서의 다음 요청은 "다른 기기에서 로그인이 감지되었습니다"라는 메시지와 함께 로그인 화면으로 리다이렉트된다.
- BR-AUTH-016: 로그아웃 시 서버 세션이 즉시 삭제된다. 이후 동일 세션 ID로 접근 시 로그인 화면으로 이동된다.
- BR-AUTH-017: 세션 만료 5분 전 UI에 "세션이 N분 후 만료됩니다" 경고를 표시하고, 사용자가 [연장] 버튼을 클릭하면 타이머를 리셋한다.

**감사로그**: 로그아웃, 세션 만료, 복수 세션 탐지 이벤트 기록

---

### FS-AUTH-004: 인증 계층 추상화
**URS 참조**: UR-AUTH-005

**기능 설명**:
시스템은 인증 공급자(AuthProvider)를 인터페이스로 추상화한다. Phase 1에서는 내부 사용자 테이블을 사용하는 `LocalAuthProvider`가 구현된다. Phase 2 AD/LDAP 연동 시 `LdapAuthProvider`를 추가하고, 설정 파일에서 공급자를 전환하는 것만으로 인증 방식이 교체된다.

사용자 테이블은 `external_id`(외부 IdP의 식별자)와 `auth_provider`(공급자 코드: `LOCAL`, `LDAP`) 컬럼을 포함하여, 향후 계정 매핑을 지원한다.

---

## 5. FS-USER: 사용자 및 역할 관리

### FS-USER-001: 사용자 계정 생성
**URS 참조**: UR-USER-001

**기능 설명**:
Admin은 사용자 관리 화면에서 신규 계정을 생성한다.

**비즈니스 규칙**:
- BR-USER-001: 사용자 ID는 시스템 전역 고유값이며, 영문·숫자·점·하이픈·언더스코어만 허용(2~50자).
- BR-USER-002: 이메일은 RFC 5322 형식으로 검증한다.
- BR-USER-003: 신규 계정의 초기 비밀번호는 Admin이 설정하며, 최초 로그인 시 변경이 강제된다.
- BR-USER-004: 계정 생성 시 최소 하나의 역할을 배정해야 한다.
- BR-USER-005: 계정 생성 직후 해당 사용자에게 초기 비밀번호 및 로그인 안내 이메일이 발송된다.

**필수 입력 항목**: 사용자 ID, 이름(Full Name), 이메일, 부서, 직위, 역할(1개 이상)

**선택 입력 항목**: 계정 유효기간(Auditor 전용), 비고

**오류 처리**:
- 중복 사용자 ID: "이미 사용 중인 사용자 ID입니다."
- 중복 이메일: "이미 등록된 이메일입니다."

**감사로그**: 계정 생성 이벤트 (생성자 ID, 신규 사용자 ID, 배정 역할, 타임스탬프)

---

### FS-USER-002: 사용자 계정 수정 및 비활성화
**URS 참조**: UR-USER-002

**기능 설명**:
Admin은 기존 계정의 정보를 수정하거나 계정을 비활성화한다. 계정은 삭제하지 않는다.

**비즈니스 규칙**:
- BR-USER-006: 비활성화된 계정은 로그인이 거부되고, 활성 세션이 있으면 즉시 만료된다.
- BR-USER-007: 비활성화된 계정의 과거 서명, 감사로그, 문서 이력은 그대로 보존된다.
- BR-USER-008: 사용자 ID는 생성 후 변경할 수 없다.
- BR-USER-009: 계정 비활성화 시 사유 입력이 필수이며, 감사로그에 기록된다.
- BR-USER-010: Admin은 자신의 계정을 비활성화할 수 없다.

**감사로그**: 계정 수정·비활성화 이벤트 (변경자, 대상 사용자, 변경 전·후 값, 사유)

---

### FS-USER-003: 역할 정의 및 관리
**URS 참조**: UR-USER-003, UR-USER-004

**기능 설명**:
시스템은 8개의 기본 역할을 사전 정의한다. Admin은 역할 관리 화면에서 각 역할에 허용되는 시스템 기능(권한)을 확인하고 수정할 수 있다.

**사전 정의 역할 및 기본 기능 권한**:

| 역할 | 주요 기능 권한 |
|---|---|
| Author | 문서 생성, 초안 편집, 파일 업로드, 워크플로 제출, 개정 시작 |
| Reviewer | 문서 검토, 검토 서명, 반려 |
| Approver | 문서 승인, 승인 서명, 반려 |
| QA | 모든 문서 QA 서명, 발행, 폐기 |
| RA | 규제 문서 열람, 다운로드 |
| Reader | 시행중 문서 열람 |
| Admin | 전체 시스템 관리(계정·역할·마스터데이터·워크플로 템플릿) |
| Auditor | 전체 문서·감사로그 읽기 전용 접근 |

**비즈니스 규칙**:
- BR-USER-011: 기본 역할(8개)은 삭제할 수 없으나 기능 권한은 수정 가능하다.
- BR-USER-012: 역할 변경 사항은 즉시 적용되며, 활성 세션 사용자의 다음 요청부터 새 권한이 적용된다.
- BR-USER-013: 한 사용자는 여러 역할을 동시에 보유할 수 있으며, 권한은 보유 역할의 합집합이 적용된다.

**감사로그**: 역할 배정·회수 이벤트 (변경자, 대상 사용자, 역할명, 이전·이후 값)

---

### FS-USER-004: Auditor 계정 기간 제한
**URS 참조**: UR-USER-005

**기능 설명**:
Auditor 역할이 포함된 계정에 유효기간(시작일, 종료일)을 설정할 수 있다.

**비즈니스 규칙**:
- BR-USER-014: 유효기간 시작일 이전에는 로그인이 거부된다.
- BR-USER-015: 종료일 자정 00:00 이후에는 로그인이 거부된다. 진행 중이던 세션은 종료일 자정에 자동 만료된다.
- BR-USER-016: 유효기간 만료 1일 전 Admin에게 알림 이메일이 발송된다.

---

## 6. FS-ACC: 접근 통제

### FS-ACC-001: 3차원 RBAC 권한 매트릭스
**URS 참조**: UR-ACC-001, UR-ACC-004

**기능 설명**:
문서 접근 권한은 (기능역할 × 문서카테고리 × 조직범위)의 3차원 매트릭스로 결정된다. 세 조건을 모두 만족해야 접근이 허용된다.

**비즈니스 규칙**:
- BR-ACC-001: 접근 판단은 모든 요청(API 호출 포함)에 서버 측에서 수행한다. 클라이언트 측 UI 숨김만으로 접근을 제한하지 않는다.
- BR-ACC-002: 사용자에게 배정된 역할이 없거나, 해당 카테고리·부서 범위 권한이 없으면 문서가 목록에 나타나지 않는다.
- BR-ACC-003: 권한 매트릭스 설정은 Admin 화면에서 카테고리·부서별 역할 체크박스 형태로 관리한다.
- BR-ACC-004: 권한 변경은 감사로그에 기록되며, 즉시 적용된다.

**권한 판단 흐름**:
```
요청 수신
  → 세션 유효성 확인 (만료 시 401)
  → 사용자의 역할 목록 조회
  → 요청 대상 문서의 카테고리·부서 확인
  → 역할×카테고리×부서 매트릭스에서 해당 액션 권한 확인
  → 허용: 처리 진행 / 거부: 403 반환
```

---

### FS-ACC-002: 기밀 프로젝트 격리
**URS 참조**: UR-ACC-002

**기능 설명**:
특정 문서에 기밀 프로젝트 코드(`project_code`)가 지정된 경우, 해당 프로젝트의 멤버로 등록된 사용자만 접근할 수 있다.

**비즈니스 규칙**:
- BR-ACC-005: 기밀 프로젝트 멤버 목록은 Admin 또는 지정된 프로젝트 오너가 관리한다.
- BR-ACC-006: 비멤버 사용자의 검색 결과, 목록, 직접 URL 접근에서 해당 문서가 반환되지 않는다. (404로 응답하여 존재 여부도 노출하지 않는다)
- BR-ACC-007: 기밀 프로젝트 멤버 변경 이력은 감사로그에 기록된다.

---

### FS-ACC-003: 다운로드 권한 분리
**URS 참조**: UR-ACC-003

**기능 설명**:
문서 열람 권한과 PDF 다운로드 권한을 독립적으로 설정한다.

**비즈니스 규칙**:
- BR-ACC-008: 다운로드 권한이 없는 사용자에게는 pdf.js 뷰어에서 다운로드 버튼이 비활성화되며, 서버 측에서도 PDF 파일 다운로드 API 요청을 거부(403)한다.
- BR-ACC-009: 기본적으로 Reader 역할은 열람만 가능하며, RA·Admin 역할에만 다운로드 권한이 부여된다. 권한 매트릭스에서 Admin이 조정 가능하다.
- BR-ACC-010: 다운로드가 허용된 사용자가 PDF를 다운로드하면 감사로그에 기록된다(사용자, 문서번호, 버전, 타임스탬프).

---

## 7. FS-DOC: 문서 관리

### FS-DOC-001: 문서 생성 및 자동 채번
**URS 참조**: UR-DOC-001, UR-DOC-002, UR-DOC-010

**기능 설명**:
Author는 신규 문서 생성 화면에서 카테고리와 필수 메타데이터를 입력하면, 시스템이 카테고리의 채번 포맷 템플릿에 따라 문서번호를 자동 부여한다.

**채번 처리 절차**:
```
1. Author가 카테고리 선택
2. 카테고리의 numbering_template 조회
3. 포맷에 필요한 입력값 수집
   - {DEPT}: 문서 작성 화면의 부서 선택 필드에서 가져옴
   - {PROD}: 제품코드 선택 필드 (카테고리에 PROD가 필요한 경우만 표시)
   - {YEAR}: 서버 현재 연도 자동 적용
4. scope에 따라 다음 SEQ 값 조회 (DB 잠금으로 중복 방지)
5. 포맷 문자열에 값 대입하여 문서번호 생성
6. DB에 Document 레코드 저장 (문서번호 확정)
```

**비즈니스 규칙**:
- BR-DOC-001: 문서번호는 DB에 저장된 시점에 확정되며, 이후 변경 불가.
- BR-DOC-002: SEQ 채번은 DB 트랜잭션 잠금(select for update)으로 동시성 충돌을 방지한다.
- BR-DOC-003: 한 번 사용된 SEQ 번호는 해당 문서가 폐기됨 상태가 되어도 재사용하지 않는다.
- BR-DOC-004: 문서 생성 직후 상태는 초안이며, 동시에 미버전(revision=null) DocumentVersion 레코드가 생성된다.

**필수 입력 항목**: 카테고리, 제목, 부서, 담당자(문서 오너)
**선택 입력 항목**: 제품코드(카테고리 요구 시 필수), 프로젝트 코드, 키워드/태그, 비고

**화면 구성**:
- 카테고리 드롭다운 (선택 시 해당 카테고리의 채번 패턴 미리보기 표시)
- 문서번호 표시 영역 (저장 전: "SOP-QC-XXX (저장 시 자동 부여)", 저장 후: 실제 번호)
- 제목, 부서, 키워드 입력 필드

---

### FS-DOC-002: 소스 파일 업로드 및 PDF 변환
**URS 참조**: UR-DOC-003, UR-DOC-004

**기능 설명**:
초안 상태의 문서 버전에 Author가 소스 파일을 업로드하면, 시스템이 비동기로 PDF를 자동 생성한다.

**처리 절차**:
```
1. Author가 파일 선택 (드래그앤드롭 또는 파일 브라우저)
2. 파일 확장자 및 크기 검증 (서버 측)
3. MinIO 원본 파일 버킷에 저장 (접근제어 전용 경로)
4. 변환 작업을 비동기 큐에 등록
5. Author에게 "업로드 완료, PDF 변환 중" 상태 표시
6. LibreOffice headless가 변환 작업 처리
7. 변환 완료 시 PDF를 MinIO PDF 버킷에 저장
8. DocumentVersion.pdf_status를 READY로 업데이트
9. Author에게 "PDF 변환 완료" 알림 발송 (앱 내 알림)
```

**비즈니스 규칙**:
- BR-DOC-005: 허용 파일 형식: `.docx`, `.xlsx`, `.pptx`, `.pdf` (최대 100MB)
- BR-DOC-006: PDF 변환 실패 시 pdf_status가 `FAILED`로 설정되고, Author에게 실패 알림 발송. 파일 재업로드 후 재시도 가능.
- BR-DOC-007: 같은 초안 버전에 파일을 다시 업로드하면 기존 소스 파일을 대체한다. 이전 파일은 MinIO에 보관되고, DocumentVersion에는 현재 파일만 연결된다.
- BR-DOC-008: 소스 파일 업로드 시 해당 버전에 체크아웃 잠금이 설정된다. 다른 Author의 파일 업로드 시도 시 "현재 다른 사용자가 편집 중입니다"를 반환한다. 잠금은 체크인(제출) 또는 Admin 강제 해제로 해소된다.

**오류 처리**:
- 미지원 형식: "지원하지 않는 파일 형식입니다. (허용: docx, xlsx, pptx, pdf)"
- 크기 초과: "파일 크기가 허용 한도(100MB)를 초과합니다."
- 변환 실패: "PDF 변환에 실패하였습니다. 파일 내용을 확인 후 다시 업로드하십시오."

---

### FS-DOC-003: PDF 워터마크
**URS 참조**: UR-DOC-006

**기능 설명**:
문서 버전이 시행중 상태로 전환될 때, 시스템이 비동기로 워터마크가 포함된 통제 PDF를 생성한다.

**워터마크 내용**:
- 헤더 영역: `[문서번호] [버전] | CONTROLLED COPY`
- 푸터 영역: `시행일: YYYY-MM-DD | 페이지 N/M`
- 모든 페이지 하단 각주: `"인쇄 또는 다운로드된 사본은 통제되지 않습니다. (Uncontrolled when printed)"`

**비즈니스 규칙**:
- BR-DOC-009: 워터마크 PDF는 원본 PDF와 별도로 MinIO에 저장된다. 열람자에게는 워터마크 PDF만 제공된다.
- BR-DOC-010: 워터마크 생성 실패 시 관리자에게 알림이 발송되고, 해당 버전은 시행중으로 전환되지 않는다.
- BR-DOC-011: 개정으로 인해 대체됨 상태로 전환된 문서의 워터마크는 `SUPERSEDED - 참고용으로만 사용 가능`으로 자동 갱신된다.

---

### FS-DOC-004: 문서 열람 (PDF 뷰어)
**URS 참조**: UR-DOC-005, UR-ACC-003

**기능 설명**:
권한이 있는 사용자는 브라우저 내에서 pdf.js 뷰어로 문서를 열람한다. 소스 파일은 별도 권한 없이는 접근 불가하다.

**비즈니스 규칙**:
- BR-DOC-012: PDF 파일은 MinIO에서 시스템이 단기 서명 URL을 생성하여 pdf.js에 전달한다. 직접 URL은 외부에 노출되지 않는다.
- BR-DOC-013: pdf.js 뷰어에서 기본으로 다운로드·인쇄 버튼은 숨겨진다. 다운로드 권한이 있는 사용자에게만 다운로드 버튼이 표시된다.
- BR-DOC-014: 문서 열람이 시작될 때(PDF 로드 시) 감사로그에 기록된다.
- BR-DOC-015: 초안 상태 문서는 Author와 지정된 워크플로 담당자만 열람 가능하다.

---

### FS-DOC-PDFVIEW-001: PDF 뷰어 인브라우저 열람
**URS 참조**: UR-DOC-006

**기능 설명**:
권한 있는 사용자가 브라우저에서 pdf.js 뷰어를 통해 PDF를 직접 열람한다. 다운로드 없이 인브라우저 렌더링이 이루어진다.

**구현**:
- `PdfViewer.vue` — pdf.js (pdfjs-dist@4.x) wrapper 컴포넌트
- `usePdfViewer.ts` — ArrayBuffer 유지, 페이지 네비게이션 composable
- `DocumentPdfView.vue` — 라우트 진입점, 권한 파라미터 전달

**제약**:
- 1024px 이상 데스크탑 환경 전제
- pdfjs-dist@4.x 사용, `isEvalSupported: false` (CSP 준수)
- 직접 MinIO URL 노출 금지 — 백엔드 스트리밍 프록시를 통해서만 제공

---

### FS-DOC-PDFVIEW-002: 렌디션 기반 접근 통제
**URS 참조**: UR-DOC-006, FS-ACC-001

**기능 설명**:
D2 권한 매트릭스 — pdf_status + rendition_kind + 역할 기반으로 접근 가능한 렌디션이 결정된다.

**구현**:
- `PdfAccessPolicy.java` — 역할·상태별 렌디션 접근 허용 여부 판단
- `PdfController.java` — `kind` / `step` 쿼리 파라미터 처리, 자동 선택 로직

**접근 규칙**:
- Author (본인 DRAFT) → `INITIAL` 렌디션 접근 가능
- 활성 단계 assignee → `STAMPED` 렌디션 접근 가능
- AUDITOR → 모든 step의 `STAMPED` 열람 가능
- 일반 열람 권한자 → `EFFECTIVE` 렌디션 (EFFECTIVE_STAMPED 상태일 때만)

**제약**:
- 접근 거부는 `ProblemDetail 404` 반환 (IDOR 보호 — 권한 없음과 존재하지 않음을 구별 불가)
- 감사로그: `PDF_VIEWED`, `PDF_VIEW_DENIED` 이벤트 기록

---

### FS-DOC-PDFVIEW-003: PDF 무결성 검증 (§11.70)
**URS 참조**: UR-DOC-006, §11.70

**기능 설명**:
사용자가 뷰어에서 "무결성 확인" 버튼을 클릭하면 Web Crypto API로 SHA-256 해시를 계산하고 서버 저장 해시 및 `signature_manifests` cross-check를 수행한다.

**구현**:
- `VerifyButton.vue` — Web Crypto SHA-256 클라이언트 측 계산 + 서버 비교 요청
- `POST /api/v1/pdf/verify-report` — 서버 측 해시 비교 + `signature_manifests` 교차 검증
- `AuditAction.PDF_VERIFIED` — 검증 결과(PASS/FAIL) + sha256 페이로드를 `audit_logs.after_value`에 기록

**제약**:
- Content-Length 선행 검증 필수 (대용량 파일 DoS 방지)
- `audit_logs` 기록 필수 — 검증 실패 시에도 FAIL 결과 기록
- 검증 결과: "✅ PASS" 또는 "❌ FAIL" 배지로 뷰어 UI에 표시

---

### FS-DOC-005: 버전 관리
**URS 참조**: UR-DOC-007, UR-DOC-008

**기능 설명**:
모든 문서 버전은 DocumentVersion 엔티티로 관리되며, Major-only 방식으로 채번된다.

**비즈니스 규칙**:
- BR-DOC-016: 문서 생성 시 `revision=null`인 초안 버전이 생성된다.
- BR-DOC-017: 최초 승인·시행 시 `revision=0`이 부여된다.
- BR-DOC-018: 이후 개정 시행마다 `revision=이전값+1`이 부여된다.
- BR-DOC-019: 대체됨 및 폐기됨 상태의 모든 버전은 소스 파일과 워터마크 PDF가 MinIO에 영구 보존된다. DB 레코드도 삭제 불가.
- BR-DOC-020: MinIO에서 파일 보관 버킷은 Object Lock(WORM) 정책이 적용되어 파일이 불변 저장된다.

---

## 8. FS-LCY: 문서 라이프사이클

### FS-LCY-001: 상태 전이 엔진
**URS 참조**: UR-LCY-001, UR-LCY-002

**기능 설명**:
시스템은 허용된 상태 전이만 처리하는 상태머신을 구현한다. 모든 전이는 ① 현재 상태 유효성, ② 요청자 권한, ③ 워크플로 조건의 3단계를 모두 통과해야 수행된다.

**상태 전이 정의**:

| 전이 ID | From | To | 트리거 | 필요 권한 | 조건 |
|---|---|---|---|---|---|
| T-01 | 초안 | 검토중 | 제출 | Author | 소스 파일 존재, 워크플로 인스턴스 생성 완료, 작성 서명 완료 |
| T-02 | 검토중 | 초안 | 반려 | 현 단계 검토자 | 반려 사유 입력 필수 |
| T-03 | 검토중 | 승인중 | 검토 완료 | 현 단계 검토자 전원 | 현 검토 단계 모든 서명자 서명 완료 |
| T-04 | 승인중 | 검토중 | 반려 | 현 단계 승인자 | 반려 사유 입력 필수 |
| T-05 | 승인중 | 시행중 | 최종 승인 | 워크플로 마지막 단계 서명자 | 모든 단계 서명 완료 |
| T-06 | 시행중 | 개정중 | 개정 시작 | Approver 이상 | 동일 문서번호의 개정중 버전이 없어야 함 |
| T-07 | 개정중→초안 완료 후 승인 | 시행중 | 최종 승인 | 워크플로 마지막 단계 서명자 | 자동으로 이전 시행중→대체됨 전환 (원자적 트랜잭션) |
| T-08 | 시행중 | 폐기됨 | 폐기 | QA | 폐기 사유 입력 필수 |

**비즈니스 규칙**:
- BR-LCY-001: 위 표에 없는 전이를 시도하면 HTTP 422와 함께 "허용되지 않는 상태 전이입니다"를 반환한다.
- BR-LCY-002: 모든 상태 전이는 전자서명(FS-SIG-001)이 수반된다.
- BR-LCY-003: T-07 실행 시, 이전 시행중→대체됨 전환과 신규 시행중 진입은 하나의 DB 트랜잭션 내에서 원자적으로 처리된다.

---

### FS-LCY-002: 워크플로 템플릿 관리
**URS 참조**: UR-LCY-007, UR-LCY-008

**기능 설명**:
Admin은 각 문서 카테고리에 대해 결재 단계(Step)를 정의하는 워크플로 템플릿을 설정한다.

**템플릿 데이터 구조**:
```
WorkflowTemplate
  category_id      : 대상 카테고리 ID
  steps (ordered) : [
    {
      step_order   : 1, 2, 3 ...
      type         : REVIEW | APPROVAL
      role         : REVIEWER | APPROVER | QA | RA
      min_signers  : 최소 서명자 수 (기본값: 1)
      parallel     : true (병렬, 모두 서명) | false (단수)
      auto_assign  : true (시스템 자동 지정) | false (Author 지정)
      qa_required  : true인 단계가 1개라도 있어야 시행중 전환 허용
    }
  ]
  qa_mandatory     : true이면 qa_required 단계를 삭제 불가
```

**비즈니스 규칙**:
- BR-LCY-004: 템플릿에 적어도 1개의 APPROVAL 단계가 있어야 저장된다.
- BR-LCY-005: `qa_mandatory=true` 카테고리에서 `qa_required=true` 단계를 삭제 시도 시, "이 카테고리는 QA 최종 승인이 필수입니다" 경고와 함께 삭제를 거부한다.
- BR-LCY-006: 템플릿 변경은 변경 시점 이후 새로 생성되는 워크플로 인스턴스부터 적용된다. 진행 중인 워크플로 인스턴스에는 적용되지 않는다.
- BR-LCY-007: 템플릿 변경 이력(변경 전·후 전체 템플릿 JSON)은 감사로그에 기록된다.

---

### FS-LCY-003: 워크플로 인스턴스 생성 (담당자 지정)
**URS 참조**: UR-LCY-006

**기능 설명**:
Author가 문서를 제출(T-01)하기 전, 워크플로 인스턴스 생성 화면에서 각 단계의 담당자를 지정한다.

**처리 절차**:
```
1. Author가 [제출 준비] 버튼 클릭
2. 카테고리의 워크플로 템플릿 기반으로 단계 목록 표시
3. auto_assign=false 단계: Author가 해당 role 보유 사용자를 검색하여 배정
4. auto_assign=true 단계: 시스템이 해당 role 보유 활성 사용자 중 배정
   (단일 사용자면 자동, 복수면 Admin이 사전에 기본 담당자를 설정)
5. Author가 [확인] 클릭 시 WorkflowInstance 및 WorkflowStep 레코드 생성
6. 이후 전자서명(작성 완료) 진행 → 상태 초안→검토중 전환
```

**비즈니스 규칙**:
- BR-LCY-008: 각 단계의 min_signers 수 이상을 배정해야 한다.
- BR-LCY-009: Author는 자신을 검토자 또는 승인자로 배정할 수 없다.
- BR-LCY-010: auto_assign 단계의 담당자는 Author가 변경할 수 없다. 담당자 변경이 필요하면 Admin만 가능하며, 변경 시 감사로그에 기록된다.
- BR-LCY-011: 워크플로 인스턴스 및 단계 정보는 시행 완료 후에도 영구 보존되며 수정·삭제할 수 없다.

---

### FS-LCY-004: 시행중 단일성 보장
**URS 참조**: UR-LCY-003

**기능 설명**:
동일 문서번호에 대해 시행중 상태인 버전은 항상 정확히 1개만 존재한다.

**비즈니스 규칙**:
- BR-LCY-012: T-05(승인→시행중) 및 T-07(개정중→시행중) 처리 시, DB 트랜잭션 내에서 UNIQUE 제약(document_id, state='EFFECTIVE')을 통해 동시에 2개의 시행중이 존재하는 것을 DB 레벨에서 방지한다.
- BR-LCY-013: 동시 처리 충돌 발생 시, 트랜잭션이 롤백되고 "처리 중 충돌이 발생하였습니다. 다시 시도하십시오."를 반환한다.

---

### FS-LCY-005: 개정 중 기존 시행중 열람
**URS 참조**: UR-LCY-004

**기능 설명**:
개정중 상태에서, 현행 시행중 버전은 권한 있는 사용자가 계속 열람 가능하다.

**비즈니스 규칙**:
- BR-LCY-014: 개정중 상태에서 문서 목록은 시행중(현행 버전)을 기본으로 표시한다.
- BR-LCY-015: 초안(개정 중인 새 버전)은 Author와 워크플로 배정자만 접근 가능하다.

---

### FS-LCY-006: 워크플로 이력 보존
**URS 참조**: UR-LCY-009

**기능 설명**:
완료된 모든 워크플로 인스턴스의 단계, 서명자, 서명 일시, 반려 이력을 문서 이력 화면에서 조회할 수 있다.

**화면 구성 (문서 이력 탭)**:
```
버전 Rev 1 — 시행중
  ├─ 작성 완료 : 홍길동 (Author) — 2026-03-01 09:30 KST
  ├─ 검토 완료 : 이순신 (Reviewer) — 2026-03-03 11:00 KST
  │               김영희 (Reviewer) — 2026-03-03 14:00 KST  [병렬]
  ├─ 승인 완료 : 박민준 (Approver) — 2026-03-05 10:00 KST
  └─ QA 승인  : 최QA  (QA)       — 2026-03-07 16:00 KST
```

---

## 9. FS-SIG: 전자서명

### FS-SIG-001: 서명 처리 플로우
**URS 참조**: UR-SIG-001, UR-SIG-002, UR-SIG-003, UR-SIG-004, UR-SIG-005

**기능 설명**:
워크플로 단계 완료, 상태 전이 등 서명이 필요한 액션에서 서명 다이얼로그가 표시된다. 사용자는 비밀번호를 재입력하여 신원을 확인하고, 서명 의미를 선택 후 서명을 완료한다.

**서명 다이얼로그 구성**:
```
┌────────────────────────────────────────────┐
│           전자서명 확인                    │
├────────────────────────────────────────────┤
│ 서명 문서 : SOP-QC-001 Rev 1               │
│ 서명 의미 : [검토 완료 ▼]  (이 액션에서   │
│             허용된 Meaning만 표시)         │
│ 코멘트   : [____________________________]  │
│                                            │
│ 서명자   : 홍길동 (hong.gildong)   (고정) │
│ 서명 시각 : 2026-05-08 14:35:22 KST (자동) │
│                                            │
│ 비밀번호 재입력 : [**************]         │
│                                            │
│ [취소]                         [서명 완료] │
└────────────────────────────────────────────┘
```

**서명 처리 절차**:
```
1. 사용자가 [서명 완료] 클릭
2. 서버에서 비밀번호 재인증
   - 세션 첫 번째 서명: 사용자 ID와 비밀번호 모두 검증
   - 동일 세션 이후 서명: 비밀번호만 검증
3. 실패 시: 오류 메시지 표시, 잠금 카운터 증가
4. 성공 시:
   a. SignatureManifest 레코드 INSERT
      (signer_id, meaning, comment, signed_at=서버시각, ip,
       prev_hash, this_hash=SHA-256(이전hash+signer_id+meaning+signed_at))
   b. 상태 전이 처리 (FS-LCY-001)
   c. 워크플로 단계 완료 처리
   d. 비동기: 서명 페이지 PDF 갱신 (FS-SIG-003)
   e. 다음 단계 담당자에게 알림 발송
```

**비즈니스 규칙**:
- BR-SIG-001: 서명 다이얼로그는 열린 후 5분 내에 완료하지 않으면 자동 닫힘. 서명 처리 없음.
- BR-SIG-002: 서명 실패(잘못된 비밀번호)는 로그인 실패 카운터와 동일한 잠금 정책 적용.
- BR-SIG-003: 서명자 표시 이름과 ID는 다이얼로그에 고정 표시되며 사용자가 변경 불가.
- BR-SIG-004: 서명 시각은 서버 NTP 동기화 시각을 사용하며, 클라이언트 제공 시각은 무시.
- BR-SIG-005: 완료된 서명은 삭제·수정 불가. 취소 필요 시 새 버전 생성 후 재진행.

---

### FS-SIG-002: 서명 매니페스트 해시체인 (v2 canonical_payload)
**URS 참조**: UR-SIG-006

**기능 설명**:
각 서명 레코드는 canonical_payload를 기반으로 계산된 SHA-256 해시(this_hash)로 연결된다. prev_hash는 직전 레코드의 this_hash이며 canonical_payload와 별도 컬럼으로 저장된다. 체인이 깨지면 변조가 탐지된다.

**canonical_payload v2 형식** (algorithm_version = 'v2'):
```
8-field pipe-delimited, NFC 정규화 후 역슬래시 이스케이프:
  \\ → \\ (역슬래시 자체)
  \| → | (파이프 문자)

형식:
  {signer_id}|{meaning}|{signed_at_iso}|{version_id}|{doc_number}|{revision}|{doc_status}|{source_file_sha256}

예시:
  42|REVIEWED|2026-05-12T10:30:00Z|77|SOP-QC-001|1|UNDER_REVIEW|a3f9...

genesis:
  prev_hash = HEX(SHA-256("GENESIS"))  ← 리터럴 문자열이 아닌 해시값
```

**해시 계산 방식**:
```
canonical_payload = serialize(signer_id, meaning, signed_at_iso, version_id,
                              doc_number, revision, doc_status, source_file_sha256)
this_hash = HEX(SHA-256(prev_hash + canonical_payload))
```

**algorithm_version 컬럼**:
- 기존 레코드(마이그레이션 전): `v1`
- 신규 레코드(M6 이후): `v2`
- `this_hash` UNIQUE 제약조건(V20) 적용

**체인 무결성 뷰**:
- `v_signature_chain_integrity` — 연속 레코드 간 prev_hash = 직전 this_hash 여부 조회

**체인 검증 API**:
- `/api/v1/documents/{docId}/versions/{versionId}/signatures/verify`
- 응답: `{ valid: true/false, broken_at: null | signature_id }`
- Admin 및 Auditor가 호출 가능

---

### FS-SIG-003: 서명 페이지 자동 생성
**URS 참조**: UR-SIG-007

**기능 설명**:
문서 버전이 시행중으로 전환될 때, 해당 버전의 모든 서명 정보를 담은 서명 페이지가 PDF 마지막에 자동으로 추가된다.

**서명 페이지 구성**:
```
━━━━━━━━━━━━━ 전자서명 이력 ━━━━━━━━━━━━━

  문서번호 : SOP-QC-001 Rev 1
  시행일   : 2026-03-07

  ┌──────────┬──────────┬────────────┬──────────────────────┐
  │ 서명 의미│ 서명자   │ 서명 일시  │ 코멘트               │
  ├──────────┼──────────┼────────────┼──────────────────────┤
  │ 작성 완료│ 홍길동   │ 2026-03-01 │ 초안 작성 완료       │
  │ 검토 완료│ 이순신   │ 2026-03-03 │                      │
  │ 검토 완료│ 김영희   │ 2026-03-03 │ 내용 확인함          │
  │ 승인     │ 박민준   │ 2026-03-05 │                      │
  │ QA 최종  │ 최QA     │ 2026-03-07 │ 검토 이상 없음       │
  └──────────┴──────────┴────────────┴──────────────────────┘

  이 문서는 21 CFR Part 11 준수 전자서명으로 승인되었습니다.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### FS-SIG-004: 서명 시 비밀번호 재인증 및 잠금 정책
**URS 참조**: UR-SIG-003

**기능 설명**:
서명 요청 시 비밀번호 재인증이 반드시 수행된다. 비밀번호 검증 실패는 로그인 잠금 카운터와 동일한 정책을 따른다.

**비즈니스 규칙**:
- BR-SIG-010: `verifyPassword()` 호출 시 계정 상태(LOCKED, DISABLED)를 먼저 확인한다. LOCKED/DISABLED 계정은 비밀번호 정확 여부와 무관하게 서명 거부(403).
- BR-SIG-011: 비밀번호 실패 시 잠금 카운터 증가 트랜잭션은 `REQUIRES_NEW` 독립 트랜잭션으로 처리되어 서명 롤백과 무관하게 커밋된다.
- BR-SIG-012: 연속 5회 실패 시 계정 상태 = LOCKED. 이후 서명/로그인 모두 거부.

---

### FS-SIG-005: 서명 매니페스트 기록 필드
**URS 참조**: UR-SIG-004

**기능 설명**:
서명 성공 시 `signature_manifests` 테이블에 다음 필드가 기록된다.

| 필드 | 설명 |
|---|---|
| version_id | 서명 대상 문서 버전 ID |
| workflow_step_id | 해당 워크플로 단계 ID |
| signer_id | 서명자 PK |
| signer_user_id | 서명자 로그인 ID |
| signer_name | 서명자 표시 이름 |
| meaning | 서명 의미 (REVIEWED 등) |
| signed_at | 서버 NTP 시각 (UTC ISO 8601) |
| client_ip | 서명 요청 클라이언트 IP |
| canonical_payload | v2 8-field pipe 직렬화 문자열 |
| prev_hash | 직전 서명의 this_hash (genesis: HEX(SHA-256("GENESIS"))) |
| this_hash | SHA-256(prev_hash ∥ canonical_payload), UNIQUE |
| session_first | 세션 내 첫 번째 서명 여부 (boolean) |
| algorithm_version | 'v1' 또는 'v2' |

---

### FS-SIG-007: 서명 목록 조회 API (2-tier)
**URS 참조**: UR-SIG-008

**기능 설명**:
`GET /api/v1/documents/{docId}/versions/{vid}/signatures` — 해당 버전의 서명 목록을 서명 순서 오름차순으로 반환한다.

**2-tier 응답**:
- **공개 필드** (Reviewer 이상): signer_name, meaning, signed_at, comment
- **특권 필드** (Admin/Auditor 추가 제공): signer_user_id, client_ip, this_hash, prev_hash, algorithm_version, session_first, canonical_payload

**응답 코드**:
- 200: 서명 목록 (배열, 빈 경우 `[]`)
- 401: 미인증
- 403: 조회 권한 없음
- 404: 문서/버전 없음

---

### FS-SIG-008: 서명 차단 조건
**URS 참조**: UR-SIG-001, UR-SIG-003

**기능 설명**:
다음 조건 중 하나라도 해당하면 서명이 거부된다.

| 차단 조건 | 응답 코드 | 오류 코드 |
|---|---|---|
| 비밀번호 오류 | 401 | SIGNATURE_001 |
| 계정 LOCKED 또는 DISABLED | 403 | SIGNATURE_003 |
| 담당자 아닌 사용자 서명 시도 | 403 | — |
| 이미 서명한 단계 재서명 시도 | 409 | — |
| 워크플로 단계 IN_PROGRESS 아님 | 409 | — |
| 분당 요청 횟수 초과 (5req/min per userId+IP) | 429 | RATE_LIMIT_001 |
| 세션 첫 서명이나 signing_user_id 미제공 | 422 | SIGNATURE_002 |
| signing_user_id 불일치 | 403 | SIGNATURE_003 |

---

### FS-SIG-009: session_first 판별 및 markSigned() 소비 시점
**URS 참조**: UR-SIG-002

**기능 설명**:
`session_first = true`인 조건: 동일 HttpSession 객체에서 최초로 sign()을 성공 완료하는 서명.

**규칙**:
- BR-SIG-015: HttpSession에 서명 완료 마커가 없으면 `session_first = true`.
- BR-SIG-016: `markSigned()` — HttpSession에 완료 마커 기록 — 은 `signature_manifests` INSERT 성공 이후에만 호출된다. INSERT 실패 또는 롤백 시 마커 기록 없음.
- BR-SIG-017: Tomcat 기본 세션 idle timeout(30분)과 동기화. 세션 만료 후 재로그인 시 다시 `session_first = true`.

---

## 10. FS-AUD: 감사추적

### FS-AUD-001: 감사로그 자동 생성 규칙
**URS 참조**: UR-AUD-001, UR-AUD-002, UR-AUD-003

**기능 설명**:
시스템은 아래 이벤트에 대해 감사로그를 자동으로 생성한다. 사용자가 로깅 여부를 선택하거나 로그를 생략할 수 없다.

**로깅 이벤트 목록**:

| 카테고리 | 이벤트 코드 | 트리거 조건 |
|---|---|---|
| 인증 | LOGIN_SUCCESS | 로그인 성공 |
| 인증 | LOGIN_FAIL | 로그인 실패 |
| 인증 | ACCOUNT_LOCKED | 계정 잠금 |
| 인증 | LOGOUT | 로그아웃 |
| 인증 | SESSION_EXPIRED | 세션 타임아웃 |
| 인증 | PWD_CHANGED | 비밀번호 변경 |
| 사용자 | USER_CREATED | 계정 생성 |
| 사용자 | USER_MODIFIED | 계정 정보 수정 |
| 사용자 | USER_DISABLED | 계정 비활성화 |
| 사용자 | ROLE_ASSIGNED | 역할 배정 |
| 사용자 | ROLE_REVOKED | 역할 회수 |
| 문서 | DOC_CREATED | 문서 생성 |
| 문서 | DOC_METADATA_CHANGED | 메타데이터 수정 |
| 문서 | FILE_UPLOADED | 파일 업로드 |
| 문서 | DOC_VIEWED | 문서 열람 (PDF 로드) |
| 문서 | DOC_DOWNLOADED | PDF 다운로드 |
| 라이프사이클 | STATE_TRANSITION | 상태 전이 |
| 서명 | SIGNED | 전자서명 완료 |
| 서명 | SIGN_FAIL | 서명 실패 (잘못된 PW) |
| 권한 | PERMISSION_CHANGED | 권한 매트릭스 변경 |
| 관리 | TEMPLATE_CHANGED | 워크플로·채번 템플릿 변경 |
| 관리 | MASTER_DATA_CHANGED | 마스터데이터 변경 |
| 감사 | AUDIT_LOG_EXPORTED | 감사로그 내보내기 |

**감사로그 레코드 구조**:

| 필드 | 설명 |
|---|---|
| `id` | 자동 증가 정수 PK |
| `event_code` | 위 이벤트 코드 |
| `user_id` | 행위자 사용자 ID |
| `user_name` | 행위자 이름 (계정 변경과 무관하게 당시 이름 기록) |
| `entity_type` | 대상 엔티티 유형 (DOCUMENT, USER, ROLE 등) |
| `entity_id` | 대상 엔티티 ID |
| `before_value` | 변경 전 값 (JSON, 해당 시) |
| `after_value` | 변경 후 값 (JSON, 해당 시) |
| `reason` | 사유 (상태 전이·서명 시 필수) |
| `ip_address` | 요청 IP |
| `user_agent` | 브라우저/클라이언트 정보 |
| `server_timestamp` | 서버 NTP 기준 UTC 타임스탬프 |
| `prev_hash` | 이전 레코드의 this_hash |
| `this_hash` | SHA-256(prev_hash + 주요 필드들) |

---

### FS-AUD-002: 감사로그 INSERT-only 보호
**URS 참조**: UR-AUD-004

**기능 설명**:
`audit_log` 테이블은 INSERT 전용 DB 권한으로 보호된다. 애플리케이션 DB 계정은 INSERT만 가능하며, UPDATE/DELETE 권한이 없다. 별도 관리 계정도 audit_log 테이블에 대한 UPDATE/DELETE를 수행할 수 없다.

**비즈니스 규칙**:
- BR-AUD-001: audit_log 테이블에 대한 ALTER, DROP, TRUNCATE도 금지된다.
- BR-AUD-002: 감사로그 조회 권한은 Admin과 Auditor 역할에 한정된다.
- BR-AUD-003: 감사로그 내보내기(CSV)를 수행하면 "AUDIT_LOG_EXPORTED" 이벤트가 감사로그에 기록된다.

---

### FS-AUD-003: 해시체인 무결성 및 WORM 앵커링
**URS 참조**: UR-AUD-005

**기능 설명**:
감사로그 레코드는 해시체인으로 연결되며, 매일 자정 일별 체크포인트를 MinIO Object Lock 버킷에 저장한다.

**일별 앵커링 프로세스**:
```
1. 매일 자정(00:00 KST) 스케줄러 실행
2. 해당 날짜의 전체 audit_log 레코드 해시 집계
   (Merkle root 방식: 당일 레코드의 this_hash를 SHA-256으로 순차 집계)
3. 앵커 파일 생성:
   {
     "date": "2026-05-07",
     "record_count": 1247,
     "first_id": 8830,
     "last_id": 10076,
     "merkle_root": "a3f2...",
     "prev_anchor_hash": "b8e1...",
     "anchor_hash": "c9d2..."
   }
4. MinIO Object Lock 버킷에 저장 (파일명: audit-anchor-2026-05-07.json)
   Object Lock: COMPLIANCE mode, retention 3650일(10년)
   -- GOVERNANCE는 권한자가 우회 가능하므로 감사 anchor에 부적합; COMPLIANCE 필수
5. DB에 anchor 레코드 저장 (체크포인트 이력 관리)
```

**검증 API**:
- `/api/v1/audit/anchors/{date}/verify` — 특정 날짜 앵커 검증
- `/api/v1/audit/verify-range?from={date}&to={date}` — 기간 전체 연속성 검증

---

## 11. FS-SRCH: 검색 및 조회

### FS-SRCH-001: 통합 검색
**URS 참조**: UR-SRCH-001, UR-SRCH-002, UR-SRCH-003, UR-SRCH-004

**기능 설명**:
상단 검색 바에서 키워드를 입력하면 메타데이터와 문서 내용을 동시에 검색한다.

**검색 처리**:
```
1. 검색어 입력 (최소 2자)
2. 서버에서 사용자의 접근 가능 문서 범위 필터 적용
3. 다음 대상을 동시 검색:
   a. 메타데이터: 문서번호, 제목, 태그 (LIKE 또는 tsvector 인덱스)
   b. 전문 검색: PDF 추출 텍스트의 tsvector 컬럼 (mecab-ko 형태소 분석)
4. 결과 합산 및 관련도 정렬 (ts_rank)
5. 기본 필터: 시행중 상태만 반환 (대체됨·폐기됨 제외)
6. 사용자가 "이전 버전 포함" 체크 시 대체됨 포함
```

**필터 옵션**: 카테고리, 부서, 상태, 작성일 범위, 담당자

**비즈니스 규칙**:
- BR-SRCH-001: 접근 권한이 없는 문서는 검색 결과에서 서버 측에서 제외된다.
- BR-SRCH-002: 기밀 프로젝트 비멤버는 해당 문서가 검색 결과에 포함되지 않는다.
- BR-SRCH-003: 초안 상태 문서는 Author와 배정된 담당자에게만 검색 결과에 표시된다.

---

### FS-SRCH-002: 문서 버전 이력 조회
**URS 참조**: UR-SRCH-005

**기능 설명**:
문서 상세 화면의 [이력] 탭에서 해당 문서의 모든 버전(초안 제외)을 시간순으로 확인한다.

**표시 항목**: 버전(Rev N), 상태, 시행일, 폐기일/대체일, 최종 서명자, 서명 일시

---

## 12. FS-NTFY: 알림 및 정기검토

### FS-NTFY-001: 워크플로 이벤트 알림
**URS 참조**: UR-NTFY-001, UR-NTFY-004

**기능 설명**:
워크플로 이벤트 발생 시 이메일과 앱 내 알림센터를 통해 관련 사용자에게 알린다.

**이벤트별 수신자 및 알림 내용**:

| 이벤트 | 이메일 수신자 | 내용 |
|---|---|---|
| 검토 요청 | 현 단계 검토자 전원 | "SOP-QC-001 검토를 요청합니다." + EDMS 링크 |
| 승인 요청 | 현 단계 승인자 전원 | "SOP-QC-001 승인을 요청합니다." |
| 반려 | Author | "SOP-QC-001이 반려되었습니다. 사유: ..." |
| 시행 발행 | 교육 대상자 전원 | "SOP-QC-001 Rev 1이 시행되었습니다." |
| 서명 완료 | 다음 단계 담당자 | 다음 검토/승인 단계 요청 |

**앱 내 알림센터**:
- 로그인 사용자는 우측 상단 벨 아이콘에서 미처리 알림 수를 확인한다.
- 알림 목록은 최신순 정렬, 각 항목에 문서명·발생 시각·내용 요약·[이동] 링크를 표시한다.
- 알림은 SSE(Server-Sent Events)로 실시간 수신된다.
- 사용자가 읽은 알림은 읽음 처리되어 카운트에서 제외된다.

---

### FS-NTFY-002: 정기검토 알림 스케줄러
**URS 참조**: UR-NTFY-002, UR-NTFY-003

**기능 설명**:
시스템은 매일 09:00 KST에 정기검토 기한 알림 스케줄러를 실행하고, 해당하는 문서의 담당자에게 알림을 발송한다.

**알림 발송 기준**:
```
시행중 문서의 next_review_date와 오늘 날짜 비교:
- next_review_date - 오늘 = 90일 → D-90 알림 → 문서 담당자(Owner)
- next_review_date - 오늘 = 30일 → D-30 알림 → Owner + 소속 부서장
- next_review_date - 오늘 = 7일  → D-7 알림  → Owner + 부서장 + Admin
- next_review_date < 오늘        → Overdue    → 관리자 대시보드 OVERDUE 표시 (매일 갱신)
```

**정기검토 처리 흐름 (검토 확인)**:
```
1. 담당자가 대시보드 또는 이메일 링크로 해당 문서 접근
2. "정기검토 수행" 버튼 클릭
3. 선택: [변경 없음 확인] 또는 [개정 시작]
4. [변경 없음 확인] 선택 시:
   - 서명 다이얼로그 표시 (Meaning: PERIODIC_REVIEW_CONFIRMED)
   - 서명 완료 후 next_review_date = 오늘 + review_period_months
   - 감사로그에 정기검토 완료 기록
5. [개정 시작] 선택 시:
   - 새 초안 버전 생성 (T-06 처리)
   - 정기검토는 개정 완료(시행중 전환) 시 자동 완료 처리
```

**비즈니스 규칙**:
- BR-NTFY-001: 정기검토 미이행은 문서 열람을 차단하지 않는다 (Advisory 정책).
- BR-NTFY-002: 관리자 대시보드에서 Overdue 문서 목록을 내보낼 수 있다.

---

## 13. FS-TRN: 교육 및 배포

### FS-TRN-001: 교육 과제 생성 및 이수 확인
**URS 참조**: UR-TRN-001, UR-TRN-002, UR-TRN-003

**기능 설명**:
문서가 시행중으로 전환될 때, 교육 대상자 규칙에 따라 교육 과제(TrainingAssignment)가 생성되고, 사용자는 PDF를 열람한 후 이수 확인 서명을 완료한다.

**교육 과제 생성 흐름**:
```
1. 문서 시행중 전환 이벤트 트리거
2. 문서의 training_rule 조회:
   - 기본값: 카테고리×부서 소속 사용자 중 활성 계정 전체
   - Author가 추가 지정한 사용자 포함
3. 각 대상자에 TrainingAssignment 레코드 생성
   (document_version_id, user_id, assigned_at=현재, due_date=시행일+30일)
4. 대상자 전원에게 교육 요청 이메일 발송
5. 앱 내 알림센터에 "미이수 교육 N건" 표시
```

**이수 확인 서명 흐름**:
```
1. 사용자가 교육 과제 목록에서 문서 클릭
2. PDF 뷰어 화면 하단에 [읽고 이해하였음] 버튼 표시
   (버튼은 PDF 로드 완료 후 활성화)
3. 사용자가 버튼 클릭 → 서명 다이얼로그 (Meaning: TRAINING_COMPLETED)
4. 서명 완료 시:
   - TrainingAssignment.completed_at = 서버 시각 설정
   - TrainingAssignment.completion_signature_id 연결
   - 감사로그 기록
5. 교육 과제 목록에서 해당 항목 완료 처리
```

**미이수 리마인더**:
- due_date - 7일: 미이수자에게 리마인더 이메일
- due_date 경과: Admin 대시보드에 미이수자 목록 표시

---

## 14. FS-ADMIN: 시스템 관리

### FS-ADMIN-001: 채번 포맷 템플릿 관리
**URS 참조**: UR-DOC-002, UR-ADMIN-001

**기능 설명**:
Admin은 카테고리별 채번 포맷 템플릿을 설정하고 변경한다.

**설정 화면 구성**:
- 카테고리 선택 드롭다운
- 포맷 문자열 입력 필드 (`{TYPE}-{DEPT}-{SEQ:3}` 형태)
- 카운터 범위(scope) 선택 (`PER_DEPT` / `PER_PRODUCT` / `PER_YEAR` / `GLOBAL`)
- 미리보기: 입력된 포맷으로 생성될 번호 예시 실시간 표시
- [저장] 버튼

**비즈니스 규칙**:
- BR-ADMIN-001: 포맷 문자열에 `{SEQ:N}`은 반드시 포함되어야 한다.
- BR-ADMIN-002: `{PROD}` 플레이스홀더가 포함된 경우, 문서 생성 화면에서 제품코드 선택 필드가 표시된다.
- BR-ADMIN-003: 포맷 변경 이후 이미 부여된 번호에는 영향을 주지 않는다.
- BR-ADMIN-004: 포맷 변경 이력은 감사로그에 변경 전·후 전체 포맷 문자열과 함께 기록된다.

---

### FS-ADMIN-002: 워크플로 템플릿 관리
**URS 참조**: UR-LCY-007, UR-LCY-008

**기능 설명**:
Admin은 카테고리별 워크플로 템플릿을 설정하고 변경한다.

**설정 화면 구성**:
- 카테고리 선택
- 단계 목록 (드래그로 순서 변경 가능)
- 각 단계: 유형(검토/승인), 역할, 최소서명자, 병렬여부, 자동배정여부, QA필수여부 설정
- [단계 추가] / [단계 삭제] 버튼
- `qa_mandatory` 토글 (활성화 시 qa_required 단계 삭제 불가)

---

### FS-ADMIN-003: 관리자 대시보드
**URS 참조**: UR-ADMIN-003

**기능 설명**:
Admin 로그인 후 대시보드에서 시스템 현황을 한눈에 파악한다.

**대시보드 위젯**:
1. **문서 상태 현황** — 상태별 문서 수 (시행중 / 검토중 / 승인중 / 초안) 막대차트
2. **정기검토 Overdue** — 기한 초과 문서 목록 (문서번호, 제목, 기한, 담당자)
3. **미서명 대기** — 워크플로 대기 중인 문서 목록 (단계, 담당자, 경과 일수)
4. **최근 시스템 이벤트** — 최근 24시간 로그인·서명·상태전이 이벤트 요약

---

## 15. FS-PERF: 성능 및 가용성

### FS-PERF-001: 응답시간 목표
**URS 참조**: UR-PERF-001, UR-PERF-002

**기능 설명**:
다음 응답시간 기준을 충족해야 한다 (동시 사용자 50명 기준, 네트워크 지연 제외).

| 기능 | 목표 응답시간 |
|---|---|
| 문서 목록 조회 (50건) | 2초 이내 |
| PDF 뷰어 초기 로딩 (10MB 이하) | 5초 이내 |
| 전자서명 처리 | 3초 이내 |
| 검색 결과 반환 | 3초 이내 |
| 파일 업로드 완료 (50MB) | 30초 이내 |

동시 사용자 100명 조건에서 위 기준의 150% 이내 (예: 목록 조회 3초)를 유지해야 한다.

---

## 16. FS-SEC: 보안

### FS-SEC-001: 전송 보안
**URS 참조**: UR-SEC-001, UR-SEC-003, UR-SEC-004, UR-SEC-005

**비즈니스 규칙**:
- BR-SEC-001: 모든 HTTP 요청은 HTTPS(TLS 1.2 이상)로 처리. HTTP 요청은 HTTPS로 301 리다이렉트.
- BR-SEC-002: 모든 API 요청의 Content-Type을 서버에서 검증. 예상치 못한 형식은 거부.
- BR-SEC-003: 모든 사용자 입력값은 서버 측에서 검증·이스케이프 처리. XSS, SQL 인젝션, 경로 탐색 패턴 탐지 및 거부.
- BR-SEC-004: 파일 업로드 시 파일 내용을 검사하여 실행 코드(예: .js, .sh) 또는 악성 매크로가 포함된 파일을 거부.
- BR-SEC-005: 모든 타임스탬프는 NTP 동기화된 서버 시각(UTC 저장, KST 표시) 사용.

---

## 17. FS-BKP: 백업 및 복구

### FS-BKP-001: 백업 정책
**URS 참조**: UR-BKP-001, UR-BKP-002, UR-BKP-003, UR-BKP-004

**백업 계획**:

| 대상 | 주기 | 방식 | 보관 위치 | 보관 기간 |
|---|---|---|---|---|
| PostgreSQL | 매일 Full + 매시간 WAL | pg_basebackup + WAL 아카이빙 | 별도 NAS (암호화) | 90일 |
| MinIO 원본 버킷 | 실시간 복제 | MinIO Replication | DR 사이트 MinIO | 영구 |
| MinIO PDF 버킷 | 실시간 복제 | MinIO Replication | DR 사이트 MinIO | 영구 |
| 감사로그 앵커 | 매일 | WORM Object Lock | MinIO Object Lock 버킷 | 10년 (Object Lock) |
| 설정 파일 | 주 1회 | 파일 백업 | 별도 NAS | 1년 |

**DR 전환 절차**:
1. 기본 사이트 장애 감지
2. DR PostgreSQL 최신 WAL 적용 (최대 1시간 데이터 유실)
3. DR MinIO는 실시간 복제로 최신 상태 유지
4. DNS/L4 스위치를 DR 사이트로 전환
5. 목표: RTO 4시간 이내, RPO 1시간 이내

---

## 18. FS-MIG: 데이터 이관

### FS-MIG-001: 벌크 임포트 도구
**URS 참조**: UR-MIG-001, UR-MIG-002, UR-MIG-003, UR-MIG-004

**기능 설명**:
CLI 도구(`edms-import`)를 통해 기존 통제문서를 EDMS로 일괄 이관한다.

**입력 형식**:
```
import/
  metadata.csv          ← 문서 메타데이터
  files/
    SOP-QC-001_Rev0.docx
    MET-QC-001_Rev2.pdf
    ...
```

**metadata.csv 컬럼**:
```
doc_number, title, category, dept, owner_id, revision,
state, effective_date, next_review_date, source_file_path,
keywords, project_code, migration_note
```

**처리 절차**:
```
1. CSV 파싱 및 유효성 검증
   (필수 필드 누락, doc_number 중복, category/dept 코드 유효성)
2. 유효성 검증 오류 레코드는 error_report.csv에 기록, 건너뜀
3. 유효 레코드 처리:
   a. Document 및 DocumentVersion 레코드 생성
   b. 소스 파일 MinIO 업로드
   c. PDF 변환 (소스가 .docx/.xlsx인 경우)
   d. 워터마크 적용 (시행중 상태인 경우)
   e. migration_origin = "IMPORT-YYYYMMDD" 기록
   f. 상태를 PENDING_QA_APPROVAL로 설정
4. 완료 후 import_report.csv 생성:
   (성공 건수, 실패 건수, 각 레코드 처리 결과)
```

**QA 마이그레이션 승인**:
```
임포트 완료 → QA가 대시보드에서 이관 문서 목록 확인
  → [이관 승인] 버튼 클릭
  → 서명 다이얼로그 (Meaning: MIGRATION_APPROVED)
  → 서명 완료 시 상태 → 시행중 전환
  → 감사로그 기록
```

**비즈니스 규칙**:
- BR-MIG-001: QA 마이그레이션 서명 없이는 이관 문서가 시행중 상태로 전환되지 않는다.
- BR-MIG-002: 이관 도구는 production DB에 직접 접근하지 않고, Admin API를 통해 처리한다.
- BR-MIG-003: 동일 doc_number가 이미 DB에 존재하면 임포트를 거부하고 오류 보고서에 기록한다.

---

## 19. URS-FS 추적성 매트릭스

| URS ID | FS ID | 충족 여부 |
|---|---|---|
| UR-AUTH-001 | FS-AUTH-001 | ✅ |
| UR-AUTH-002 | FS-AUTH-002 | ✅ |
| UR-AUTH-003 | FS-AUTH-001 (BR-AUTH-004~005) | ✅ |
| UR-AUTH-004 | FS-AUTH-003 (BR-AUTH-013~014) | ✅ |
| UR-AUTH-005 | FS-AUTH-004 | ✅ |
| UR-AUTH-006 | FS-AUTH-003 (BR-AUTH-015) | ✅ |
| UR-USER-001 | FS-USER-001 | ✅ |
| UR-USER-002 | FS-USER-002 | ✅ |
| UR-USER-003 | FS-USER-003 | ✅ |
| UR-USER-004 | FS-USER-003 | ✅ |
| UR-USER-005 | FS-USER-004 | ✅ |
| UR-ACC-001 | FS-ACC-001 | ✅ |
| UR-ACC-002 | FS-ACC-002 | ✅ |
| UR-ACC-003 | FS-ACC-003 | ✅ |
| UR-ACC-004 | FS-ACC-001 (BR-ACC-002) | ✅ |
| UR-DOC-001 | FS-DOC-001 | ✅ |
| UR-DOC-002 | FS-DOC-001, FS-ADMIN-001 | ✅ |
| UR-DOC-003 | FS-DOC-002 | ✅ |
| UR-DOC-004 | FS-DOC-002 | ✅ |
| UR-DOC-005 | FS-DOC-004 | ✅ |
| UR-DOC-006 | FS-DOC-PDFVIEW-001, FS-DOC-PDFVIEW-002, FS-DOC-PDFVIEW-003 | ✅ |
| UR-DOC-007 | FS-DOC-005 | ✅ |
| UR-DOC-008 | FS-DOC-005 (BR-DOC-019~020) | ✅ |
| UR-DOC-009 | FS-DOC-002 (BR-DOC-008) | ✅ |
| UR-DOC-010 | FS-DOC-001 | ✅ |
| UR-LCY-001 | FS-LCY-001 | ✅ |
| UR-LCY-002 | FS-LCY-001 | ✅ |
| UR-LCY-003 | FS-LCY-004 | ✅ |
| UR-LCY-004 | FS-LCY-005 | ✅ |
| UR-LCY-005 | FS-LCY-001 (T-02, T-04) | ✅ |
| UR-LCY-006 | FS-LCY-003 | ✅ |
| UR-LCY-007 | FS-LCY-002 | ✅ |
| UR-LCY-008 | FS-LCY-002 (BR-LCY-005) | ✅ |
| UR-LCY-009 | FS-LCY-006 | ✅ |
| UR-SIG-001 | FS-SIG-001 | ✅ |
| UR-SIG-002 | FS-SIG-001 (서명 의미 종류) | ✅ |
| UR-SIG-003 | FS-SIG-001 (BR-SIG-001~002) | ✅ |
| UR-SIG-004 | FS-SIG-001 (세션 첫/이후 서명) | ✅ |
| UR-SIG-005 | FS-SIG-001 (BR-SIG-005) | ✅ |
| UR-SIG-006 | FS-SIG-002 | ✅ |
| UR-SIG-007 | FS-SIG-003 | ✅ |
| UR-SIG-008 | FS-SIG-001 (BR-SIG-001) | ✅ |
| UR-AUD-001 | FS-AUD-001 | ✅ |
| UR-AUD-002 | FS-AUD-001 (레코드 구조) | ✅ |
| UR-AUD-003 | FS-AUD-001 (reason 필드) | ✅ |
| UR-AUD-004 | FS-AUD-002 | ✅ |
| UR-AUD-005 | FS-AUD-003 | ✅ |
| UR-AUD-006 | FS-AUD-002, FS-BKP-001 | ✅ |
| UR-AUD-007 | FS-AUD-002 (BR-AUD-002~003) | ✅ |
| UR-SRCH-001 | FS-SRCH-001 | ✅ |
| UR-SRCH-002 | FS-SRCH-001 | ✅ |
| UR-SRCH-003 | FS-SRCH-001 (BR-SRCH-001) | ✅ |
| UR-SRCH-004 | FS-SRCH-001 (BR-SRCH-003) | ✅ |
| UR-SRCH-005 | FS-SRCH-002 | ✅ |
| UR-NTFY-001 | FS-NTFY-001 | ✅ |
| UR-NTFY-002 | FS-NTFY-002 | ✅ |
| UR-NTFY-003 | FS-NTFY-002 (검토 확인 흐름) | ✅ |
| UR-NTFY-004 | FS-NTFY-001 (앱 내 알림센터) | ✅ |
| UR-TRN-001 | FS-TRN-001 | ✅ |
| UR-TRN-002 | FS-TRN-001 | ✅ |
| UR-TRN-003 | FS-TRN-001 | ✅ |
| UR-TRN-004 | FS-TRN-001 (LMS 확장 포인트) | ✅ |
| UR-ADMIN-001 | FS-ADMIN-001, FS-ADMIN-002 | ✅ |
| UR-ADMIN-002 | FS-ADMIN-002 | ✅ |
| UR-ADMIN-003 | FS-ADMIN-003 | ✅ |
| UR-PERF-001 | FS-PERF-001 | ✅ |
| UR-PERF-002 | FS-PERF-001 | ✅ |
| UR-PERF-003 | FS-PERF-001 (가용성 목표) | ✅ |
| UR-SEC-001 | FS-SEC-001 (BR-SEC-001) | ✅ |
| UR-SEC-002 | FS-AUTH-002 (BR-AUTH-008~010) | ✅ |
| UR-SEC-003 | FS-DOC-004 (BR-DOC-012) | ✅ |
| UR-SEC-004 | FS-SIG-001 (BR-SIG-004) | ✅ |
| UR-SEC-005 | FS-SEC-001 (BR-SEC-003) | ✅ |
| UR-BKP-001 | FS-BKP-001 | ✅ |
| UR-BKP-002 | FS-BKP-001 | ✅ |
| UR-BKP-003 | FS-BKP-001 | ✅ |
| UR-BKP-004 | FS-AUD-003, FS-BKP-001 | ✅ |
| UR-MIG-001 | FS-MIG-001 | ✅ |
| UR-MIG-002 | FS-MIG-001 (migration_origin) | ✅ |
| UR-MIG-003 | FS-MIG-001 (QA 마이그레이션 승인) | ✅ |
| UR-MIG-004 | FS-MIG-001 (import_report.csv) | ✅ |

**추적성 요약**: URS 78개 요구사항 → FS 전량 추적 완료 (78/78)

---

## 20. 변경 이력

| 버전 | 날짜 | 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 초안 작성 (VAL-URS-001 v0.3 기반, 78개 요구사항 전량 반영) | TBD |

---

*본 문서는 검토자 및 QA 승인 후 공식 버전으로 발행된다. 승인 전까지는 초안(Draft) 상태이며 구현의 참조용으로만 사용한다.*
