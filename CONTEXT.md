# CONTEXT.md — EDMS 프로젝트 진입점

> **대상**: AI 코드 생성 에이전트 및 신규 개발자.  
> 이 문서를 먼저 읽으면 어디에 무엇이 있는지, 무엇을 지켜야 하는지 5분 안에 파악할 수 있다.

---

## 1. 프로젝트 정체성

**제약 R&D 연구소용 GxP Electronic Document Management System(EDMS).**  
전자서명·감사추적·문서 라이프사이클·역할 기반 접근 통제를 갖추며, 21 CFR Part 11 / EU GMP Annex 11을 준수한다. 폐쇄망(air-gapped) 온프레미스로 운영된다.

- **Phase 1.a 범위**: SOP / Method / Specification / Form 문서 카테고리
- **핵심 기능**: 전자서명, SHA-256 해시체인 감사추적, WORM 보관, 3차원 RBAC, 채번 자동화, 워크플로 엔진

---

## 2. 현재 상태 (2026-05-12)

| 항목 | 상태 |
|---|---|
| 검증 문서 패키지 | ✅ 완성 — 31개 파일 (V-Model 전 산출물 + 11개 SOP + 컴플라이언스 매트릭스 + 가이드) |
| 백엔드 구현 | ✅ M7.1 완료 (M1~M7 + PdfController/PdfAccessPolicy/VerifyButton 백엔드) |
| 프론트엔드 구현 | ✅ M7.1 완료 (pdf.js 뷰어, 권한 분리, Verify) |
| IQ/OQ/PQ 실행 | ⬜ 구현 완료 후 |
| 파일럿 배포 | ⬜ OQ/PQ 통과 후 |

### 마일스톤 진행 현황

| 마일스톤 | 내용 | 상태 |
|---|---|---|
| M1 | 기반 인프라 + 감사추적 해시체인 | ✅ |
| M2 | User & RBAC (PermissionEvaluator·8 역할·단일 세션) | ✅ |
| M3 | 문서 채번 자동화 + MinIO 업로드 | ✅ |
| M4 | 문서 라이프사이클 워크플로 | ✅ |
| M5 | 감사 하드닝 + WORM Audit Anchor | ✅ |
| M6 | E-Signature 강화 (canonical_payload + UNIQUE + 첫 서명 ID+PW + 잠금 + 조회) | ✅ |
| M7 | PDF 파이프라인 + documents 버킷 GOVERNANCE | ✅ |
| M7.1 | PDF 뷰어 (pdf.js + Verify + 권한 매트릭스) | ✅ |

**다음 행동**: M8 (결재 인박스 / SignatureDialog / NotificationCenter) 또는 IQ/OQ 실행

---

## 3. 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Spring Boot 3.3 / Java 21 / Spring Security 6 / Spring Data JPA / Hibernate Envers |
| Database | PostgreSQL 16 — Flyway 10 마이그레이션 |
| Object Storage | MinIO (Object Lock COMPLIANCE 모드) |
| PDF 변환 | LibreOffice 7.x headless |
| PDF 워터마크 | Apache PDFBox 3.x |
| PDF 뷰어 | pdf.js (Mozilla) — in-browser, 다운로드 권한 분리 |
| Frontend | Vue 3 Composition API / TypeScript / Vite |
| 검색 | PostgreSQL Full-Text Search + mecab-ko (한국어 형태소) |
| 배포 (dev) | Docker Compose |
| 배포 (IQ·prod) | Ansible |
| 시크릿 관리 | HashiCorp Vault (prod) / sealed-config (IQ) / env 파일 (dev) |
| 보안 인프라 | 사내 PKI / NTP (시그니처 타임스탬프 보장) |
| 감사 불변성 | Hibernate Envers + audit_logs INSERT-only + MinIO WORM anchor |

---

## 4. 컴플라이언스 범위

| 규정 | 적용 범위 | 증거 문서 |
|---|---|---|
| 21 CFR Part 11 | Subpart B §11.10(a)~(k), §11.50, §11.70, §11.100, §11.200, §11.300 | `validation/Compliance-Part11.md` |
| EU GMP Annex 11 | §1~§17 전체 (§15 Batch Release — N/A, R&D 범위) | `validation/Compliance-Annex11.md` |
| GAMP 5 (2nd Ed.) | Category 5 — Customised application | `validation/ValidationPlan.md` §4 |
| ALCOA+ | Attributable / Legible / Contemporaneous / Original / Accurate / Complete / Consistent / Enduring / Available | `validation/SOPs/SOP-DATA-INTEGRITY-001.md` |

---

## 5. 리포지토리 구조

```
DMS/
├── CONTEXT.md                  ← 본 문서 (진입점)
└── validation/                 GxP CSV 산출물 (31 파일)
    │
    ├── URS.md                  요구사항 (78 요건, VAL-URS-001)
    ├── FS.md                   기능 명세 (VAL-FS-001)
    ├── DS.md                   기술 설계 — 구현의 기준 (VAL-DS-001)
    ├── RiskAssessment.md       위험 분석 (VAL-RA-001)
    ├── TraceabilityMatrix.md   URS↔FS↔DS↔Test 양방향 추적성 (VAL-TM-001)
    ├── ValidationPlan.md       CSV 계획 (VAL-VP-001)
    ├── ValidationReport_template.md
    │
    ├── IQ_protocol.md          설치 적격성 프로토콜 (VAL-IQ-001)
    ├── IQ_report.md
    ├── OQ_protocol.md          운전 적격성 프로토콜 (VAL-OQ-001)
    ├── OQ_report.md
    ├── PQ_protocol.md          성능 적격성 프로토콜 (VAL-PQ-001)
    ├── PQ_report.md
    │
    ├── Compliance-Part11.md    21 CFR Part 11 조항별 대응 매트릭스 (VAL-CM-001)
    ├── Compliance-Annex11.md   EU Annex 11 조항별 대응 매트릭스 (VAL-CM-002)
    ├── openapi.yaml            OAS 3.1 API 계약 (전 엔드포인트 + 스키마)
    │
    ├── SOPs/                   11개 운영 SOP
    │   ├── SOP-USER-001.md     계정·역할 관리
    │   ├── SOP-CHANGE-001.md   변경 통제
    │   ├── SOP-PERIODIC-001.md 정기검토
    │   ├── SOP-BACKUP-001.md   백업·복구
    │   ├── SOP-DR-001.md       재해 복구 훈련
    │   ├── SOP-CICD-001.md     소스관리·CI/CD·배포
    │   ├── SOP-AUDIT-TRAIL-001.md  감사추적 검토
    │   ├── SOP-DATA-INTEGRITY-001.md  데이터 무결성 관리
    │   ├── SOP-DEVIATION-001.md    운영 일탈 관리
    │   ├── SOP-INCIDENT-001.md     보안사고 대응
    │   └── SOP-TRAINING-001.md     사용자 교육·자격
    │
    ├── architecture/
    │   ├── Network.md          네트워크 존·포트·방화벽 정책 (VAL-NET-001)
    │   └── SecretsManagement.md  시크릿 인벤토리·보관·회전·노출 대응 (VAL-SEC-001)
    │
    └── guides/
        ├── UserManual.md       사용자 가이드 (Author/Reviewer/Approver/Owner) (EDMS-UG-001)
        └── AdminGuide.md       관리자 가이드 (계정·권한·워크플로·모니터링) (EDMS-AG-001)

(향후: backend/ / frontend/ / migration/ / infra/ — 구현 착수 시 추가)
```

---

## 6. 검증 V-Model

```
요구사항 정의          기술 설계                  검증 실행
─────────────────────────────────────────────────────────
  URS.md   →  FS.md  →  DS.md  →  [구현]
    │            │          │           │
   PQ ◄──────  OQ  ◄──────  IQ  ◄──── (테스트)
         └──────────────────────────────┘
              TraceabilityMatrix.md
         (URS ↔ FS ↔ DS ↔ OQ/PQ 케이스 양방향)
```

**권장 읽기 순서** (개발자 기준):  
`URS.md` → `FS.md` → `DS.md §3~10` → `openapi.yaml` → `SOPs/SOP-CICD-001.md`

**권장 읽기 순서** (AI 에이전트 기준):  
태스크 분야에 따라 §9 가이드 참조.

---

## 7. 핵심 아키텍처 결정

| 결정 | 내용 | 근거 문서 |
|---|---|---|
| 폐쇄망 온프레미스 | 인터넷 egress 전면 차단, 사내 Nexus mirror만 허용 | `architecture/Network.md` |
| 3차원 RBAC | 역할 × 문서카테고리 × 부서범위 교차 권한 | `DS.md §4.2` (permissions 테이블) |
| SHA-256 해시체인 | `signature_manifests`·`audit_logs` 전 레코드 연결 — 변조 즉시 탐지 | `DS.md §8.1, §8.2` |
| WORM 앵커링 | 일별 해시체인 anchor를 MinIO Object Lock COMPLIANCE 10년 보관 | `DS.md §8.3` |
| 세션 첫 서명 ID+PW | 첫 번째 서명만 ID+비밀번호 입력, 이후는 비밀번호만 — Part 11 §11.200(a) | `DS.md §6.5`, `openapi.yaml #SignRequest` |
| 서버 NTP 타임스탬프 | 모든 이벤트에 서버 시간만 사용, 클라이언트 시간 거부. NTP 오차 >10초 시 서명 거부 | `DS.md §10.5` |
| Major-only 버전 | 개정 시 Major 번호만 증가(Rev 0/1/2), Minor 하위 버전 없음 | `FS.md FS-DOC-006` |
| audit_logs INSERT-only | DB 역할 분리: `audit_writer` 역할만 INSERT 허용, UPDATE/DELETE 금지 | `DS.md §4.3` |
| 단일 세션 강제 | Spring Security maximumSessions(1) — 계정 공유 탐지 | `DS.md §5.1` |
| **M5: anchor 체인** | anchor_hash = SHA-256(prev\|merkle\|date\|count\|first\|last). genesis = SHA-256("ANCHOR_GENESIS"). RFC 6962 Merkle promote (홀수 노드 promote, duplication 아님). | `DS.md §8.3` |
| **M5: KST 일자 경계** | audit_logs.server_ts(UTC) → `AT TIME ZONE 'Asia/Seoul'` 캐스팅으로 KST 일자 경계 구분. WormAnchorJob은 어제(KST)까지 catchup. | `DS.md §8.3` |
| **M5: signature_manifests 권한** | V18 마이그레이션에서 app_role의 UPDATE/DELETE/TRUNCATE 권한 박탈 (V15 누락 보강). INSERT/SELECT는 audit_role에만 부여. | `DS.md §4.3` |
| **M6: canonical_payload v2 직렬화** | 8-field pipe-delimited: `signer_id\|meaning\|signed_at_iso\|version_id\|doc_number\|revision\|doc_status\|source_file_sha256`. NFC 정규화 + 역슬래시 이스케이프. algorithm_version 컬럼(DEFAULT v1, 신규 v2). genesis = HEX(SHA-256("GENESIS")). this_hash UNIQUE (V20). | `DS.md §8.1`, `FS-SIG-002` |
| **M6: session_first + ID+PW** | 첫 서명 시 signing_user_id 필수(SIGNATURE_002/003). markSigned() 호출 시점 = signature_manifests INSERT 성공 이후. Tomcat 30분 idle timeout과 동기. | `DS.md §6.5.1`, `FS-SIG-009` |
| **M6: Rate Limiting** | Bucket4j 5req/min per (userId+IP) → 429 RATE_LIMIT_001. LOCKED/DISABLED 계정 verifyPassword() 거부(403). | `DS.md §6.5`, `FS-SIG-008` |
| **M7: PDF 파이프라인 상태머신** | Document.pdf_status: PENDING_CONVERSION → CONVERTED → STAMPING → STAMPED → WATERMARKING → EFFECTIVE_STAMPED. Gotenberg(LibreOffice) 변환, PDFBox stamp 누적, EFFECTIVE 워터마크. MinIO COMPLIANCE 10년 retention. | `DS.md §5.4`, `PdfRenditionPipeline` |
| **M7: canonical_payload v3** | v2(8-field) + rendition_sha256 = 9-field. SignatureCanonicalSerializer.serializeV3. §11.70 RENDITION hash stamp 직접 충족. algorithm_version='v3'. | `DS.md §8.1`, `SignatureCanonicalSerializer` |
| **M7: EFFECTIVE 워터마크 스케줄러** | EffectiveWatermarkScheduler — 매일 00:05 KST. ShedLock leader election(shedlock 테이블, V22). effectiveDate=오늘 & pdfStatus=STAMPED 버전 자동 처리. | `EffectiveWatermarkScheduler`, V22 migration |

---

## 8. 코딩·작업 컨벤션

| 규칙 | 내용 |
|---|---|
| **언어** | 문서·UI 텍스트·주석 → 한국어 / 코드·식별자·로그·패키지명 → 영어 |
| **DB 변경** | 반드시 Flyway 마이그레이션 파일로만. `ALTER TABLE` 직접 실행 금지 |
| **브랜치** | `main`(릴리즈) / `develop` / `feature/*` / `hotfix/*` |
| **PR 리뷰** | 최소 2인 승인 필수. Part 11 영향 기능(서명·감사·접근통제)은 QA 리뷰어 추가 |
| **시크릿** | 평문 코드·리포지토리 커밋 절대 금지. 환경변수 또는 Vault 주입 |
| **감사로그 절대 금지** | `audit_logs`, `signature_manifests` 테이블 UPDATE/DELETE 절대 금지 |
| **타임스탬프** | 클라이언트 전달 시간 신뢰 금지. 항상 `NOW()` (서버, NTP 동기화) 사용 |
| **변경 통제** | Major 이상 기능 변경은 SOP-CHANGE-001 적용 (영향 평가·QA 승인 후 배포) |
| **에러 응답** | RFC 7807 ProblemDetail 형식. `openapi.yaml #ProblemDetail` 스키마 준수 |

---

## 9. 시작 가이드

### AI 에이전트 (Claude Code 등)

본 CONTEXT.md 읽기(1분) 후 태스크 분야에 따라:

| 태스크 | 읽을 문서 |
|---|---|
| 백엔드 도메인·DB | `validation/DS.md §3~6, §8` |
| REST API 엔드포인트·스키마 | `validation/openapi.yaml` |
| 프론트엔드 화면 흐름 | `validation/DS.md §7` + `validation/guides/UserManual.md` |
| 관리자 기능 | `validation/guides/AdminGuide.md` |
| 보안·시크릿·네트워크 | `validation/architecture/` 두 파일 |
| 운영 절차 | `validation/SOPs/` (해당 SOP 직접) |
| 컴플라이언스 확인 | `validation/Compliance-Part11.md` 또는 `Compliance-Annex11.md` |

### 신규 개발자

1. 본 CONTEXT.md (5분)
2. `validation/URS.md` — 도메인 이해, 78개 요건 훑기 (30분)
3. `validation/DS.md §3~10` — 구현 기준 전체 (1시간)
4. `validation/openapi.yaml` — API 계약, 스키마 정의 (30분)
5. `validation/SOPs/SOP-CICD-001.md` — 브랜치·리뷰·배포 절차 (15분)

---

## 10. 용어집

| 용어 | 정의 |
|---|---|
| EDMS | Electronic Document Management System — 본 시스템 |
| GxP | Good Practice 총칭 (GMP, GLP, GCP). 규제 대상 의약품·임상 관련 품질 기준 |
| CSV | Computerized System Validation — 컴퓨터화 시스템 검증 |
| GAMP 5 Cat 5 | 맞춤 개발 애플리케이션. 가장 높은 검증 요건 적용 |
| ALCOA+ | 데이터 무결성 9원칙 (Attributable/Legible/Contemporaneous/Original/Accurate + Complete/Consistent/Enduring/Available) |
| Part 11 §11.200 | 전자서명 구성요소: 세션 첫 서명은 ID+PW, 이후는 PW 단독 |
| WORM | Write Once, Read Many — MinIO Object Lock COMPLIANCE 모드로 구현 |
| 라이프사이클 7단계 | `DRAFT` → `UNDER_REVIEW` → `UNDER_APPROVAL` → `EFFECTIVE` → `SUPERSEDED` → `RETIRED` → `ARCHIVED` |
| qa_mandatory | 워크플로 인스턴스 설정값. `true`이면 QA 서명 없이 EFFECTIVE 전이 불가 |
| Read & Acknowledge | 사용자가 문서를 열람하고 ACKNOWLEDGED 의미 전자서명으로 확인하는 행위 (교육 이수 증거) |
| 채번 템플릿 | 카테고리별 문서번호 자동 생성 규칙 (예: `SOP-{DEPT}-{SEQ:3}` → `SOP-QC-001`) |
| 워크플로 인스턴스 | 문서 버전 제출 시 생성되는 검토·승인 진행 상태 추적 객체 |
| IQ / OQ / PQ | Installation / Operational / Performance Qualification — V-Model 3단계 검증 실행 |
| Envers | Hibernate Envers — JPA 엔티티 변경 이력을 `_AUD` 테이블에 자동 기록 |
| WORM anchor | 일별 audit_logs 해시체인의 마지막 hash를 MinIO에 Object Lock으로 저장한 무결성 증거 파일 |

---

## 11. 검증 문서 ID → 경로 매핑

| 문서 ID | 경로 |
|---|---|
| VAL-URS-001 | `validation/URS.md` |
| VAL-FS-001 | `validation/FS.md` |
| VAL-DS-001 | `validation/DS.md` |
| VAL-RA-001 | `validation/RiskAssessment.md` |
| VAL-TM-001 | `validation/TraceabilityMatrix.md` |
| VAL-VP-001 | `validation/ValidationPlan.md` |
| VAL-VR-001 | `validation/ValidationReport_template.md` |
| VAL-IQ-001 | `validation/IQ_protocol.md` |
| VAL-IQ-RPT-001 | `validation/IQ_report.md` |
| VAL-OQ-001 | `validation/OQ_protocol.md` |
| VAL-OQ-RPT-001 | `validation/OQ_report.md` |
| VAL-PQ-001 | `validation/PQ_protocol.md` |
| VAL-PQ-RPT-001 | `validation/PQ_report.md` |
| VAL-CM-001 | `validation/Compliance-Part11.md` |
| VAL-CM-002 | `validation/Compliance-Annex11.md` |
| VAL-NET-001 | `validation/architecture/Network.md` |
| VAL-SEC-001 | `validation/architecture/SecretsManagement.md` |
| EDMS-UG-001 | `validation/guides/UserManual.md` |
| EDMS-AG-001 | `validation/guides/AdminGuide.md` |
| *(OAS 계약)* | `validation/openapi.yaml` |
| SOP-USER-001 | `validation/SOPs/SOP-USER-001.md` |
| SOP-CHANGE-001 | `validation/SOPs/SOP-CHANGE-001.md` |
| SOP-PERIODIC-001 | `validation/SOPs/SOP-PERIODIC-001.md` |
| SOP-BACKUP-001 | `validation/SOPs/SOP-BACKUP-001.md` |
| SOP-DR-001 | `validation/SOPs/SOP-DR-001.md` |
| SOP-CICD-001 | `validation/SOPs/SOP-CICD-001.md` |
| SOP-AUDIT-TRAIL-001 | `validation/SOPs/SOP-AUDIT-TRAIL-001.md` |
| SOP-DATA-INTEGRITY-001 | `validation/SOPs/SOP-DATA-INTEGRITY-001.md` |
| SOP-DEVIATION-001 | `validation/SOPs/SOP-DEVIATION-001.md` |
| SOP-INCIDENT-001 | `validation/SOPs/SOP-INCIDENT-001.md` |
| SOP-TRAINING-001 | `validation/SOPs/SOP-TRAINING-001.md` |

---

*본 문서는 인덱스이며 검증 문서 원본이 아니다. 각 항목의 정의·세부 요건은 해당 문서를 참조한다.*
