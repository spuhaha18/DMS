# Validation Plan (검증 계획서)
## Electronic Document Management System (EDMS)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-VP-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| 작성자 | TBD |
| 검토자 | TBD |
| 승인자 | TBD (QA Manager) |

---

## 목차

1. [목적 및 범위](#1-목적-및-범위)
2. [참조 문서 및 규정](#2-참조-문서-및-규정)
3. [시스템 설명](#3-시스템-설명)
4. [GAMP 5 분류 및 검증 접근법](#4-gamp-5-분류-및-검증-접근법)
5. [검증 산출물 및 일정](#5-검증-산출물-및-일정)
6. [역할 및 책임](#6-역할-및-책임)
7. [리스크 기반 검증 전략](#7-리스크-기반-검증-전략)
8. [테스트 환경 전략](#8-테스트-환경-전략)
9. [이탈(Deviation) 관리](#9-이탈deviation-관리)
10. [변경 관리](#10-변경-관리)
11. [검증 완료 기준](#11-검증-완료-기준)
12. [운영 후 검증 유지](#12-운영-후-검증-유지)
13. [변경 이력](#13-변경-이력)

---

## 1. 목적 및 범위

### 1.1 목적

본 문서는 제약회사 연구소(R&D)에 구축하는 EDMS(Electronic Document Management System)의 컴퓨터화 시스템 검증(CSV, Computerized System Validation) 전략과 계획을 정의한다.

검증의 목적은 시스템이 GxP 규정(21 CFR Part 11, EU GMP Annex 11)과 사용자 요구사항(URS)을 지속적으로 충족한다는 문서화된 증거를 수립하는 것이다.

본 검증 계획은 GAMP 5 (2nd Edition)의 리스크 기반 접근법을 따르며, 시스템 라이프사이클 전 단계(계획 → 개발 → 검증 → 운영 → 폐기)를 포괄한다.

### 1.2 범위

**포함**:
- Phase 1.a EDMS MVP — SOP, Method, Specification, Form 문서 카테고리
- 전자서명, 감사추적, 권한 기반 접근 통제
- 활성 통제문서 이관 (Migration)
- 운영 환경(On-Premise) 인프라

**제외 (별도 검증 계획 수립)**:
- Phase 1.b 이후 추가 기능 (Study Protocol/Report, AD/LDAP 등)
- 개발 환경·QA 환경 (운영 환경 검증이 목적)

### 1.3 검증 전략 요약

```
V-Model 적용:

  URS  ────────────────────────────────►  PQ (파일럿 운영)
    FS ──────────────────────────►  OQ (기능 검증)
      DS ─────────────────►  IQ (설치 검증)
        Code ──────►  단위/통합테스트
```

---

## 2. 참조 문서 및 규정

### 2.1 규제 참조

| 문서 | 제목 |
|---|---|
| 21 CFR Part 11 | Electronic Records; Electronic Signatures — FDA |
| EU GMP Annex 11 | Computerised Systems |
| GAMP 5 (2nd Ed.) | Risk-Based Approach to GxP Computerized Systems — ISPE |
| ICH Q9 (R1) | Quality Risk Management |
| ICH Q10 | Pharmaceutical Quality System |
| PIC/S PI 011-3 | Good Practices for Computerised Systems in Regulated GxP Environments |

### 2.2 내부 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-URS-001 v0.3 | User Requirements Specification |
| VAL-FS-001 v0.1 | Functional Specification |
| VAL-DS-001 v0.1 | Design Specification |
| VAL-RA-001 v0.1 | Risk Assessment |
| VAL-TM-001 v0.1 | Traceability Matrix |
| VAL-IQ-001 v0.1 | IQ Protocol |
| VAL-OQ-001 v0.1 | OQ Protocol |
| VAL-PQ-001 v0.1 | PQ Protocol |
| VAL-VR-001 | Validation Report (PQ 완료 후 작성) |
| SOP-CHANGE-001 | Change Control SOP |
| SOP-BACKUP-001 | Backup & Restore SOP |
| SOP-DR-001 | Disaster Recovery SOP |
| SOP-USER-001 | User Account Management SOP |
| SOP-PERIODIC-001 | Periodic Review SOP |

---

## 3. 시스템 설명

### 3.1 시스템 개요

| 항목 | 내용 |
|---|---|
| 시스템명 | EDMS (Electronic Document Management System) |
| 운영 목적 | 통제문서(SOP, Method, Spec, Form) 전 라이프사이클 관리, 전자서명, 감사추적 |
| 배포 형태 | On-Premise 폐쇄망 (사내 데이터센터) |
| 사용자 규모 | 200~500명 (단일 연구소 다부서) |
| GxP 적용 | 21 CFR Part 11 / EU Annex 11 |
| 개발 형태 | 신규 사내 개발 (Custom / Bespoke) |

### 3.2 기술 스택

| 컴포넌트 | 버전 |
|---|---|
| Backend | Spring Boot 3.3, Java 21 |
| Database | PostgreSQL 16 |
| Object Storage | MinIO (Object Lock 지원) |
| PDF 변환 | LibreOffice 7.x headless |
| Frontend | Vue 3 + TypeScript |
| PDF 뷰어 | pdf.js (Mozilla) |
| OS | Rocky Linux 9 |
| Reverse Proxy | Nginx 1.26 |

### 3.3 GxP 영향 평가

| 기능 | GxP 영향 | 근거 |
|---|---|---|
| 전자서명 | 직접 영향 (Part 11 §11.200) | 서명이 규제 기록의 일부 |
| 감사추적 | 직접 영향 (Part 11 §11.10(e)) | 기록 변경 이력 요건 |
| 문서 라이프사이클 | 직접 영향 | SOP 승인 절차가 GLP 요건 |
| 접근 통제 | 직접 영향 (Part 11 §11.10(d)) | 권한 없는 접근 방지 요건 |
| 데이터 무결성 | 직접 영향 (ALCOA+) | GxP 기록 정확성 |
| 검색 | 간접 영향 | 기록 접근 용이성 |
| 알림 | 간접 영향 | 워크플로 효율 |

---

## 4. GAMP 5 분류 및 검증 접근법

### 4.1 GAMP 5 소프트웨어 분류

**분류: Category 5 — Custom Applications (사내 개발 맞춤형)**

근거:
- 사내 개발(Custom/Bespoke) 소프트웨어
- 기성품(COTS) 패키지나 구성 가능한 제품이 아님
- 소스 코드 전체 보유 및 수정 가능
- 기능·비즈니스 로직이 사내 요구에 맞게 구현됨

Category 5에 따른 검증 요건:
- URS → FS → DS 전체 문서 작성 필수
- 코드 검토(Code Review)를 포함한 개발 단계 검증 수행
- 전체 V-Model 검증 활동 수행 (IQ/OQ/PQ)
- 소스 코드 변경 시 Change Control 수행

### 4.2 리스크 기반 검증 우선순위

VAL-RA-001의 FMEA 결과에 따라 검증 활동의 깊이와 우선순위를 결정한다.

| 위험 등급 | 검증 활동 |
|---|---|
| Critical (RPN ≥ 50 또는 S=5) | OQ 필수, 양성+음성 케이스 모두 포함, QA 직접 수행 |
| High | OQ 권장, 양성 케이스 필수, 음성 케이스 권장 |
| Medium | OQ 또는 PQ에서 확인, 샘플 기반 |
| Low | 모니터링 수준, OQ/PQ 포함 불필요 |

### 4.3 개발 단계 품질 활동

검증의 일부로 개발 단계에서 수행하는 품질 활동:

| 활동 | 시점 | 담당 |
|---|---|---|
| 단위테스트 (JUnit 5 + Testcontainers) | 개발 중 | 개발팀 |
| 통합테스트 (RestAssured) | 개발 완료 후 | 개발팀 |
| E2E 테스트 (Playwright) | Sprint 종료 | 개발팀 |
| 코드 정적분석 (SpotBugs, ESLint) | CI/CD 파이프라인 | 개발팀 |
| 보안 취약점 스캔 (OWASP Dependency-Check) | CI/CD 파이프라인 | 개발팀 |
| 코드 리뷰 (Critical 기능) | PR 머지 전 | 개발 책임자 |

---

## 5. 검증 산출물 및 일정

### 5.1 산출물 목록

| 문서 번호 | 산출물 | 단계 | 작성자 | 검토자 | 승인자(QA) |
|---|---|---|---|---|---|
| VAL-VP-001 | Validation Plan | 계획 | IT/개발팀 | 개발 책임자 | QA Manager |
| VAL-URS-001 | User Requirements Specification | 요구사항 | QA + 사용자 대표 | 부서 책임자 | QA Manager |
| VAL-FS-001 | Functional Specification | 설계 | 개발팀 | 개발 책임자 | QA |
| VAL-DS-001 | Design Specification | 설계 | 개발팀 | 개발 책임자 | QA |
| VAL-RA-001 | Risk Assessment | 설계 | QA + 개발팀 | 개발 책임자 | QA Manager |
| VAL-TM-001 | Traceability Matrix | 검증 | QA | 개발 책임자 | QA |
| VAL-IQ-001 | IQ Protocol | 검증 계획 | IT/인프라팀 | QA | QA Manager |
| VAL-OQ-001 | OQ Protocol | 검증 계획 | QA | 개발 책임자 | QA Manager |
| VAL-PQ-001 | PQ Protocol | 검증 계획 | QA | 사용자 대표 | QA Manager |
| VAL-IQ-001-R | IQ Report | 검증 수행 | 인프라팀 | QA | QA Manager |
| VAL-OQ-001-R | OQ Report | 검증 수행 | QA | 개발 책임자 | QA Manager |
| VAL-PQ-001-R | PQ Report | 검증 수행 | QA | 사용자 대표 | QA Manager |
| VAL-VR-001 | Validation Report | 완료 | QA | 개발 책임자 | QA Manager |

### 5.2 검증 일정 (권장)

> 아래 일정은 참고용이며, 팀 구성 및 자원 확정 후 확정한다.

| 단계 | 산출물 | 목표 기간 | 비고 |
|---|---|---|---|
| 계획 | Validation Plan, URS | 개발 착수 전 4주 | QA 주도, 사용자 인터뷰 병행 |
| 설계 | FS, DS, RA, TM | 개발 착수 ~ 개발 완료 전 2주 | 개발과 병행 |
| 인프라 설치 | IQ 수행 | 개발 완료 ~ +1주 | |
| OQ 수행 | OQ 수행 | IQ 합격 후 +2주 | Critical 기능 우선 |
| 데이터 이관 | PQ-MIG 수행 | OQ 합격 후 +1주 | |
| 파일럿 | PQ-PILOT 수행 | 이관 완료 후 1개월 | QC팀 파일럿 |
| 검증 완료 | Validation Report | 파일럿 완료 후 +2주 | |
| **총 검증 기간** | | **~3개월** (개발 후) | |

---

## 6. 역할 및 책임

| 역할 | 담당 | 검증 책임 |
|---|---|---|
| **QA Manager** | TBD | 검증 계획 최종 승인, Validation Report 최종 승인, Deviation 최종 판정 |
| **QA** | TBD | 검증 산출물 작성·검토·수행, 이탈 기록 관리, 추적성 매트릭스 유지 |
| **개발 책임자** | TBD | FS·DS·RA 검토, OQ 기술 지원, 이탈 시정 조치 |
| **개발팀** | TBD | FS·DS 작성, 단위·통합·E2E 테스트 수행, 이탈 수정 |
| **IT/인프라팀** | TBD | IQ 수행, 인프라 설치·설정, 백업 정책 구현 |
| **사용자 대표 (QC팀)** | TBD | URS 요구사항 확인, PQ 파일럿 참여, PQ 운영 적합성 확인 서명 |
| **부서 책임자** | TBD | URS 승인 |

### 6.1 검증 독립성

QA는 개발팀과 독립적으로 검증 활동을 수행한다. 개발팀이 작성한 코드에 대한 OQ 수행은 QA가 직접 담당한다. 단, 기술적 쿼리(DB 조회 등)는 개발팀의 지원을 받을 수 있으며, 이 경우 QA가 결과를 직접 확인하고 기록한다.

---

## 7. 리스크 기반 검증 전략

### 7.1 Critical 기능 목록 (VAL-RA-001 기준)

아래 21개 Critical 기능은 OQ에서 반드시 검증한다. OQ 프로토콜 미합격 시 시스템 릴리즈가 불가하다.

| # | 기능 | RA 항목 |
|---|---|---|
| 1 | 비인가 로그인 방지 | RA-AUTH-001 |
| 2 | 계정 잠금 | RA-AUTH-002 |
| 3 | 전자서명 비밀번호 재인증 | RA-SIG-001 |
| 4 | 서명 레코드 INSERT-only | RA-SIG-002 |
| 5 | 서명 해시체인 연속성 | RA-SIG-003 |
| 6 | 서명 의미 변조 불가 | RA-SIG-004 |
| 7 | 세션 첫 서명 ID+PW | RA-SIG-006 |
| 8 | 감사로그 INSERT-only | RA-AUD-001 |
| 9 | 감사로그 해시체인 | RA-AUD-002 |
| 10 | WORM 앵커 삭제 불가 | RA-AUD-003 |
| 11 | 감사로그 완결성 | RA-AUD-004 |
| 12 | 10년 보관 | RA-AUD-005 |
| 13 | 비허가 상태 전이 방지 | RA-LCY-001 |
| 14 | min_signers 요건 강제 | RA-LCY-002 |
| 15 | qa_mandatory 강제 | RA-LCY-003 |
| 16 | 서버 권한 체크 | RA-ACC-001 |
| 17 | 원본 파일 무결성 | RA-DOC-001 |
| 18 | PDF 변환 품질 | RA-DOC-002 |
| 19 | 파일 덮어쓰기 방지 | RA-DOC-004 |
| 20 | DB 백업 | RA-SYS-001 |
| 21 | 이관 메타데이터 정합성 | RA-MIG-001 |

### 7.2 검증 생략 대상

리스크 평가 결과 Low 등급이며 GxP 직접 영향이 없는 기능은 별도 OQ 케이스 없이 개발 단계 단위테스트로 대체한다. 해당 기능은 Traceability Matrix에 `단위테스트`로 기록한다.

---

## 8. 테스트 환경 전략

### 8.1 환경 구성

| 환경 | 목적 | 데이터 | 비고 |
|---|---|---|---|
| 개발(DEV) | 기능 개발 및 단위테스트 | 가상 데이터 | Docker Compose |
| QA/Staging | 통합·E2E 테스트 | 가상 데이터 (Prod 복사본 금지) | 운영과 동일 구성 권장 |
| 운영(PROD) | IQ/OQ/PQ 수행 | OQ: 전용 테스트 계정 / PQ: 실 데이터 | |

### 8.2 운영 환경 테스트 주의사항

OQ는 운영 환경에서 수행하며, 아래 원칙을 준수한다:
- OQ 전용 테스트 계정 사용 (실 사용자 계정 사용 금지)
- OQ 완료 후 테스트 데이터 격리 (삭제 불가 — 감사로그 포함, 별도 카테고리 또는 부서로 격리)
- OQ 수행 중 실제 업무 방해 최소화 (업무 외 시간 또는 별도 협의 후 수행)
- 음성 테스트(DB 권한 위반 시도 등)는 사전에 DBA와 협의 후 수행

---

## 9. 이탈(Deviation) 관리

### 9.1 이탈 분류

| 등급 | 기준 | 처리 |
|---|---|---|
| Critical | Critical 기능 테스트 Fail, GxP 요건 미충족 | 즉시 개발팀 에스컬레이션, 시정 완료 전 다음 단계 진행 불가 |
| Major | 주요 기능 Fail, 검증 연속성에 영향 | 48시간 이내 시정 조치 계획 수립, QA Manager 보고 |
| Minor | 비GxP 기능 Fail, 사용성 문제 | 시정 후 재검증 또는 다음 단계에서 확인 |

### 9.2 이탈 처리 절차

```
이탈 발견
  → 이탈 번호 부여 (DEV-{단계}-{순번})
  → 이탈 내용·영향 기록 (해당 프로토콜 이탈 섹션)
  → 등급 판정 (QA)
  → Critical/Major: 개발팀 즉시 통보
  → 시정 조치 수행 (개발팀)
  → 재검증 (QA)
  → 이탈 종결 (QA Manager 서명)
```

### 9.3 이탈이 Validation Report에 미치는 영향

- OQ 완료 후 미종결 Critical 이탈이 있으면 Validation Report 승인 불가
- Minor 이탈은 Validation Report에 기록 후 릴리즈 가능 (개선 계획 포함)

---

## 10. 변경 관리

### 10.1 검증 기간 중 변경

검증 수행(IQ~PQ) 중 시스템 변경이 필요한 경우:
1. 변경 요청 → QA 영향 평가
2. 영향을 받는 검증 문서 버전 갱신
3. 영향을 받는 테스트 케이스 재수행

### 10.2 검증 완료 후 변경 (운영 중 Change Control)

시스템 릴리즈 후 모든 변경은 **Change Control SOP (SOP-CHANGE-001)**을 따른다:

| 변경 분류 | 예시 | 재검증 범위 |
|---|---|---|
| 주요 변경 (Major) | 신기능 추가, 핵심 로직 변경, 인프라 서버 교체 | 영향 받는 URS/FS/DS 갱신, IQ/OQ/PQ 부분 재수행 |
| 경미한 변경 (Minor) | UI 수정, 버그 수정, 성능 튜닝 | RA 영향 평가, 필요 시 OQ 일부 재수행 |
| 긴급 변경 (Emergency) | 보안 패치, 치명적 버그 수정 | 사후 영향 평가, 사후 재검증 (Retrospective) |

---

## 11. 검증 완료 기준

Validation Report(VAL-VR-001)에 QA Manager가 서명하기 위한 필수 조건:

| # | 기준 | 산출물 |
|---|---|---|
| 1 | 모든 검증 문서(URS~PQ Report)가 QA 승인 완료 상태 | 각 문서 서명란 |
| 2 | Traceability Matrix 100% 커버리지 확인 | VAL-TM-001 |
| 3 | IQ 합격 (모든 항목 Pass 또는 이탈 종결) | VAL-IQ-001-R |
| 4 | OQ 합격 (Critical 케이스 100% Pass) | VAL-OQ-001-R |
| 5 | PQ 합격 (이관 검증 + 파일럿 완주) | VAL-PQ-001-R |
| 6 | 모든 미종결 Deviation 없음 (Critical/Major) | 각 프로토콜 이탈 기록 |
| 7 | 평행 SOP 5종 승인 완료 | SOP-CHANGE-001 외 4종 |
| 8 | 시스템 관리자 교육 완료 | 교육 기록 |
| 9 | 파일럿 부서 사용자 교육 완료 | 교육 기록 |

---

## 12. 운영 후 검증 유지

### 12.1 Periodic Review (정기 재검토)

시스템 자체에 대한 정기 재검토를 연 1회 수행한다 (SOP-PERIODIC-001 적용).

재검토 항목:
- 운영 중 발생한 Deviation 및 변경 이력 검토
- 감사로그 무결성 샘플 검증
- 백업 복구 테스트 수행 여부 확인
- 잔존 위험(Residual Risk) 재평가
- 신규 규제 요건 반영 필요 여부 검토

### 12.2 Internal Audit

사내 QA가 분기 1회 시스템 사용 감사를 수행한다:
- audit_logs 샘플 무결성 점검 (해시체인 연속성)
- 권한 매트릭스 현행성 확인 (퇴직자 계정 비활성화 여부)
- 미완료 교육 과제 현황 확인
- 정기검토 초과 문서 현황 확인

### 12.3 재검증 트리거

아래 상황에서 영향 평가 후 재검증을 수행한다:
- 주요 기능 변경 (Change Control에서 Major 분류 시)
- 인프라 서버 교체 또는 OS 업그레이드
- PostgreSQL 메이저 버전 업그레이드
- 보안 취약점 패치 이후 영향 범위 확인
- 감사에서 결함 발견 시

---

## 13. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 초안 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 승인 전까지 검증 활동의 기준으로 사용할 수 없다.*
