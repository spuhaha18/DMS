# 21 CFR Part 11 컴플라이언스 매트릭스
## EDMS — 21 CFR Part 11 Compliance Matrix

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-CM-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| 분류 | GxP 컴플라이언스 매트릭스 |
| 참조 | 21 CFR Part 11 (2003), VAL-URS-001, VAL-FS-001, VAL-DS-001 |

---

## 1. 개요

본 문서는 21 CFR Part 11 (Electronic Records; Electronic Signatures, 2003)의 각 조항이 본 EDMS에서 어떻게 충족되는지를 조항별로 매핑한다. 각 행은 **조항 번호 → 요구사항 요약 → EDMS 대응 → 증거 문서/기능 ID**로 구성된다.

**시스템 유형**: Closed System (§11.10 적용, §11.30 N/A)

**약어**:
- DS §N = VAL-DS-001 §N
- FS-xxx = VAL-FS-001 항목 ID
- SOP-xxx = 해당 SOP 문서
- OQ-xxx = VAL-OQ-001 테스트 케이스 ID

---

## 2. Subpart A — General Provisions

### §11.1 범위

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.1(a) | 본 규정은 FDA 관할 기록에 사용되는 전자기록 및 전자서명에 적용된다 | EDMS는 GLP 비임상시험 문서 및 GMP 관련 통제문서를 관리하며 Part 11 풀 준수 대상으로 분류함 | VAL-URS-001 §목적, ValidationPlan §1 |
| §11.1(b) | 규정은 종이 기록 대체 전자기록에 적용 | 종이 SOP/Form/Method를 EDMS 전자기록으로 완전 대체 | PQ_protocol §3 마이그레이션 |
| §11.2(a) | 전자기록은 해당 FDA 규정의 모든 기록 요건을 충족해야 함 | GLP 21 CFR Part 58, GMP 21 CFR Part 211 해당 기록 유형 포함 | VAL-FS-001 FS-DOC-001~010 |
| §11.2(b) | 전자서명은 수기서명과 동일한 법적 효력 | SignatureManifest에 서명자·일시·의미 불변 기록, 해시체인으로 연결 | DS §8.1, FS-SIG-005 |

---

## 3. Subpart B — Electronic Records

### §11.10 — Closed Systems

#### §11.10(a) Validation

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(a) | 시스템이 의도대로 작동함을 검증 (정확성, 신뢰성, 일관적 성능, 전자기록 위변조 탐지 능력 포함) | GAMP 5 Category 5 리스크 기반 검증. IQ/OQ/PQ 3단계 실행. 21개 Critical 기능 100% OQ 테스트 케이스 적용 | ValidationPlan (VAL-VP-001), IQ_protocol (VAL-IQ-001), OQ_protocol (VAL-OQ-001), PQ_protocol (VAL-PQ-001), RiskAssessment (VAL-RA-001) |

#### §11.10(b) Accurate and Complete Copies

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(b) | 전자기록의 정확하고 완전한 사본을 사람이 읽을 수 있는 형식과 전자 형식으로 생성·제공할 수 있어야 함 | LibreOffice → PDF 변환 (워터마크 포함). PDF 뷰어에서 렌더링. CSV 내보내기(감사로그). 인쇄 시 "Uncontrolled when printed" 푸터 자동 추가. 다운로드 권한 보유 사용자는 원본 PDF 다운로드 가능 | DS §5.4 WatermarkService, FS-DOC-004, FS-ACC-003, DS §6.3 /pdf/download |

#### §11.10(c) Record Protection

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(c) | 기록 보호 — 시스템 수명 기간 동안 및 이후에도 보호 (재생성 가능해야 함) | MinIO Object Lock GOVERNANCE 10년 (원본·PDF), COMPLIANCE 10년 (감사 앵커). PostgreSQL Logical Replication → DR. 연 단위 WORM 앵커 체크포인트. 소프트 비활성화 (물리 삭제 없음) | DS §10.2 MinIO 버킷, FS-AUD-007, FS-BKP-001~004, SOP-BACKUP-001, SOP-DR-001 |

#### §11.10(d) Limited System Access

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(d) | 전자기록 생성·수정·삭제 권한을 인가된 개인에게만 제한 | 3차원 RBAC (역할 × 카테고리 × 부서). @PreAuthorize 메서드 레벨 권한 강제. 계정 공유 금지. 분기 계정 접근 검토 | DS §9.4 PermissionEvaluator, FS-ACC-001~004, SOP-USER-001, OQ-ACC-001~011 |

#### §11.10(e) Audit Trail

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(e) | 안전한 컴퓨터 생성 감사추적 — 전자기록 생성·수정·삭제 이벤트를 운영자가 삭제 불가하도록 시간 순으로 기록. 기록 변경 시 이전 값 보존 | audit_logs: INSERT-only (app_role에서 UPDATE/DELETE REVOKE). Hibernate Envers 자동 엔티티 변경 기록. SHA-256 해시체인으로 변조 탐지. 일별 Merkle root → MinIO COMPLIANCE WORM | DS §4.2 audit_logs DDL, DS §4.3 DB 역할 분리, DS §8.2 감사로그 해시체인, DS §8.3 WORM 앵커링, FS-AUD-001~007, OQ-AUD-001~018, SOP-AUDIT-TRAIL-001 |

#### §11.10(f) Sequencing of Steps

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(f) | 기록 완성·서명 시 순서적 단계 시행 (필요 시) | 7단계 라이프사이클 상태머신 (T-01~T-09 전이 테이블). 허가되지 않은 전이 거부. 직접 DB 조작으로 상태 변경 불가 (검증 SQL로 확인) | DS §5.3 LifecycleStateMachine, FS-LCY-001~009, OQ-LCY-001~016 |

#### §11.10(g) Authority Checks

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(g) | 전자기록 생성·서명·수정·삭제 권한 확인 | 모든 API 호출 시 @PreAuthorize 권한 검증. 워크플로 단계에서 역할 확인 후 서명 허용. Admin 전용 엔드포인트 ADMIN 역할 필수 | DS §9.4, FS-ACC-001, FS-SIG-008, OQ-ACC-001~011 |

#### §11.10(h) Device Checks

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(h) | 입력 장치 출처 확인 (해당 시) | 웹 기반 시스템으로 입력 장치 다양성 내재. IP 주소 기록 (audit_logs.client_ip). 세션 기반 인증으로 미인증 장치 접근 차단. 추가 장치 바인딩: Phase 2 검토 | DS §6.1 클라이언트 IP 기록, audit_logs.client_ip |

#### §11.10(i) Personnel Qualification

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(i) | 전자기록·서명 시스템을 사용하는 개인의 교육·훈련 및 경험 보장 | 신규 계정 생성 전 EDMS 교육 이수 필수 (SOP-TRAINING-001). training_assignments 테이블에 완료 기록. 역할별 차별화 교육 커리큘럼 | SOP-TRAINING-001, DS §4.2 training_assignments, FS-TRN-001~004 |

#### §11.10(j) Accountability

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(j) | 서명 생성·삭제·수정에 대한 개인 책임 — 시스템 개발자·유지보수자·운영자 감독 | 계정 공유 금지 (SOP-USER-001 §5.5). 개인 계정으로만 로그인 가능. Admin 모든 행위 audit_logs에 actor_user_id 기록. 서명은 개인 비밀번호 재인증으로만 생성 | SOP-USER-001 §5.5, FS-SIG-004, DS §6.5 |

#### §11.10(k) Operational Checks and Controls — Documentation

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.10(k) | 시스템 운영·유지관리를 위한 적절한 통제 수립 및 문서화 | SOP 5종 (CHANGE·BACKUP·DR·USER·PERIODIC) + SOP-CICD-001 작성. IQ/OQ/PQ 검증 산출물 전체 보관. 변경 통제 SOP-CHANGE-001로 모든 변경 추적 | SOP-CHANGE-001, SOP-CICD-001, ValidationPlan, ValidationReport_template |

---

### §11.30 — Open Systems

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.30 | Open system에서는 추가 암호화·디지털서명·문서 암호화 조치 필요 | **해당 없음 (N/A)**: EDMS는 사내 폐쇄망(Closed System) 전용으로 운영됨. 인터넷 연결 없음. Egress 전면 차단 | VAL-NET-001 §5.5 |

---

## 4. Subpart C — Electronic Signatures

### §11.50 — Signature Manifestations

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.50(a) | 전자서명에 서명자 성명, 서명 일시, 서명 의미 표시 | SignatureManifest: signer_name, signed_at (NTP 서버 시각), meaning (REVIEWED/APPROVED/QA_APPROVED/ACKNOWLEDGED/RETIRED). **부분 충족** — DB 기록 완비(M6). PDF 서명 블록 stamp는 M7 PDF 파이프라인 완성 시 풀 충족. | DS §4.2 signature_manifests, FS-SIG-005, FS-SIG-006 (M7 PDF stamp) |
| §11.50(b) | 표시 정보는 기록과 동일한 형식 (인쇄, 표시)으로 제공 | 서명 매니페스트 API 및 화면 표시(M6). PDF stamp 일관성은 M7에서 완성. **부분 충족** — M7 완성 시 풀 충족. | DS §7.4 SignatureDialog.vue, FS-SIG-006 |

### §11.70 — Signature/Record Linking

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.70 | 전자서명이 서명된 전자기록에 연결되어 떼어내거나 다른 기록으로 복사할 수 없어야 함 | canonical_payload v2 (8-field pipe): `signer_id\|meaning\|signed_at_iso\|version_id\|doc_number\|revision\|doc_status\|source_file_sha256`. `this_hash = SHA256(prev_hash ∥ canonical_payload)`, UNIQUE 제약. 서명-기록 분리 불가. **부분 충족** — ORIGINAL sha256 포함(M6). RENDITION hash stamp는 M7에서 추가 예정. M7 완성 시 풀 충족. | DS §8.1 canonical_payload v2, FS-SIG-002, OQ-SIG-014, OQ-SIG-015 |

---

## 5. Subpart C (continued) — Electronic Signatures Components and Controls

### §11.100 — General Requirements

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.100(a) | 전자서명은 개인에게 고유 — 2인 이상이 공유 불가 | user_id는 UNIQUE (unique constraint). auth_provider+external_id 조합 UNIQUE. 계정 공유 금지 정책 (SOP-USER-001 §5.5). 공유 의심 시 즉시 비활성화 | DS §4.2 users.user_id UNIQUE, SOP-USER-001 §5.5 |
| §11.100(b) | 서명 이전에 서명자 신원 확인 (identity verification) | 계정 생성 시 부서 책임자 신청 + 시스템 관리자 처리. 본인 확인 없이 계정 생성 불가. Auditor 계정은 방문 목적·일정 필수 기재 | SOP-USER-001 §5.1 |
| §11.100(c) | 개인 서명 행위가 서명자 개인을 구속함을 조직이 증명 | 사용자 계정 신청 시 부서 책임자 서명. 시스템 교육 완료 후 계정 활성화 | SOP-TRAINING-001, SOP-USER-001 §5.1 |

### §11.200 — Electronic Signature Components and Controls

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.200(a)(1) | ID+PW 기반 서명 2구성요소: 세션 첫 서명은 ID+PW 둘 다, 이후 서명은 PW만 | 세션 첫 서명 감지 로직: `session_first` 판별 → `signing_user_id` 필드 검증. `session_first && signing_user_id == null` → 422 SIGNATURE_002. 불일치 → 403 SIGNATURE_003. `markSigned()` 호출은 INSERT 성공 이후에만 실행 (DS §6.5.1). | DS §6.5, §6.5.1 session_first 정의, FS-SIG-008, FS-SIG-009, SignatureFirstSignIT (OQ-SIG-009, OQ-SIG-010) |
| §11.200(a)(2) | 동일 세션에서 이후 서명은 최소 1개 구성요소 사용 가능 | 세션 내 연속 서명 시 PW 재인증만으로 가능 (signing_user_id 재입력 불요) | FS-SIG-009 (BR-SIG-015~017), DS §6.5.1 |
| §11.200(b) | 생체인식 서명: 단일 구성요소도 가능 (해당 시) | **해당 없음 (N/A)**: 생체인식 사용 안 함. ID+PW 방식 |  |

### §11.300 — Controls for Identification Codes/Passwords

| 조항 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11.300(a) | 고유성 유지 — 같은 ID 재사용 금지 (폐기 포함) | users.user_id UNIQUE constraint. 계정 비활성화(DISABLED)해도 user_id 재사용 불가 (ID는 불변) | DS §4.2 users.user_id UNIQUE, SOP-USER-001 §5.3 비활성화 원칙 |
| §11.300(b) | 비밀번호 노출·분실 시 즉시 취소 및 신규 발급 | 노출/분실 시 Admin 비밀번호 초기화 → 임시 비밀번호 개인 이메일 발송 → 첫 로그인 시 즉시 변경 강제 | SOP-USER-001 §5.2.3, DS §8.4 |
| §11.300(c) | 서명 생성 코드 발급은 서명자에게만, 개인 고지 | 임시 비밀번호: 개인 회사 이메일로 발송 + 별도 전화/방문 구두 고지. 이메일 전달 과정 감사 | SOP-USER-001 §5.1.2 |
| §11.300(d) | 전자기록 시스템에서 ID 도용 등 의심 트랜잭션 탐지·보고 가능 | 비업무 시간 로그인, 단기 과다 서명, 계정 잠금 빈번 — 이상 패턴 감지 및 보고 절차 | SOP-USER-001 §5.6, SOP-INCIDENT-001, SOP-AUDIT-TRAIL-001 §5.3 |
| §11.300(e) | ID 도용 등 시도 보고 장치 | SOP-INCIDENT-001 보안사고 분류 및 보고 체계. audit_logs 이상 패턴 알림 | SOP-INCIDENT-001 §5.3 |

---

## 6. 요약 — 조항별 준수 상태

> **상태 정의**  
> - 🔵 **Planned** — 설계 및 구현 계획 완료, 구현 미완료  
> - 🟡 **Implemented** — 코드 구현 완료, OQ/PQ 검증 미완료  
> - ✅ **Verified** — OQ/PQ 실행 및 QA 승인 완료 (운영 적용 가능)  
> - ⚠️ **Partial** — 부분 충족, 제약사항 명시  
> - ➖ **N/A** — 해당 없음  
>
> **현재 프로젝트 상태**: Phase 1.a 구현 진행 중 (M1~M4 완료 목표). 아래 상태는 설계 기준이며, "Verified"는 M12 OQ/PQ 실행 후 갱신된다.

| 조항 | 요구사항 요약 | 준수 상태 | 설계 근거 / 제약 |
|---|---|---|---|
| §11.1(a)(b) | 적용 범위 | 🔵 Planned | 폐쇄망 운영, GAMP 5 Cat 5 |
| §11.2(a)(b) | 기록·서명 법적 효력 | 🔵 Planned | SOP-DATA-INTEGRITY-001 |
| §11.10(a) | 검증 | 🔵 Planned | ValidationPlan.md, IQ/OQ/PQ 미실행 |
| §11.10(b) | 정확한 사본 생성 | 🔵 Planned | PDF 변환 + 워터마크 (M7) |
| §11.10(c) | 기록 보호 | 🔵 Planned | MinIO WORM COMPLIANCE + DR (M5) |
| §11.10(d) | 접근 제한 | 🔵 Planned | RBAC 3차원 (M2) |
| §11.10(e) | 감사추적 | 🔵 Planned | INSERT-only + 전체 필드 해시체인 (M1/M5); payload에 before/after/reason/ip 포함 |
| §11.10(f) | 단계 순서 | 🔵 Planned | 7단계 상태머신 (M4) |
| §11.10(g) | 권한 확인 | 🔵 Planned | @PreAuthorize (M2) |
| §11.10(h) | 장치 확인 | ⚠️ Partial | IP 기록. 장치 바인딩 Phase 2 |
| §11.10(i) | 인력 자격 | 🔵 Planned | SOP-TRAINING-001 |
| §11.10(j) | 책임성 | 🔵 Planned | 계정공유 금지 + 감사추적 |
| §11.10(k) | 문서 통제 | 🔵 Planned | SOP 11종 |
| §11.30 | Open system | ➖ N/A | 폐쇄망 운영 (Network.md 참조) |
| §11.50(a)(b) | 서명 표시 | ⚠️ Partial | 매니페스트 DB 기록 완비(M6). PDF stamp → M7 완성 시 풀 충족. |
| §11.70 | 서명-기록 연결 | ⚠️ Partial | canonical_payload v2 ORIGINAL sha256 포함(M6). RENDITION hash stamp → M7 완성 시 풀 충족. |
| §11.100(a)(b)(c) | 서명 고유성 | 🔵 Planned | unique constraint + 신원 확인 (M6) |
| §11.200(a) | ID+PW 구성요소 | 🟡 Implemented | 첫 서명 ID+PW session_first 판별(M6). 증거: SignatureFirstSignIT. |
| §11.200(b) | 생체인식 | ➖ N/A | 미사용 |
| §11.300(a)~(e) | 비밀번호 통제 | 🔵 Planned | BCrypt rounds=12 + SOP-USER-001 (M1) |

> **§11.10(h)**: 웹 기반 시스템 특성상 IP 기록으로 부분 충족. 장치 인증서/디바이스 등록은 Phase 2.  
> **§11.50/§11.70**: M6에서 부분 충족 — DB 기록·canonical_payload v2 완비. PDF RENDITION stamp는 M7 PDF 파이프라인 완성 시 풀 충족 예정.  
> **§11.200(a)**: M6 구현 완료 (SignatureFirstSignIT 증거). session_first + signing_user_id 검증 + markSigned() 소비 시점 = INSERT 성공 이후.  
> **상태 갱신 기준**: 각 마일스톤 OQ 통과 시 해당 조항을 "Implemented"로, M12 QA 승인 후 "Verified"로 갱신한다.

---

## 7. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 작성 | TBD |
| 0.2 | 2026-05-12 | M6 반영: §11.200(a) 증거 매핑(SignatureFirstSignIT), §11.50/§11.70 부분 충족 명기(ORIGINAL sha256 only, RENDITION stamp = M7), §11.200(a) 상태 Planned → Implemented | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 검토 전까지 최종 컴플라이언스 입장으로 사용할 수 없다.*
