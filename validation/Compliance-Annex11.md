# EU GMP Annex 11 컴플라이언스 매트릭스
## EDMS — EU GMP Annex 11 Compliance Matrix

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-CM-002 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| 분류 | GxP 컴플라이언스 매트릭스 |
| 참조 | EU GMP Annex 11 (2011), VAL-URS-001, VAL-FS-001, VAL-DS-001 |

---

## 1. 개요

본 문서는 EU GMP Annex 11 — Computerised Systems (2011년 개정)의 각 조항이 본 EDMS에서 어떻게 충족되는지를 조항별로 매핑한다.

**약어**:
- DS §N = VAL-DS-001 §N
- FS-xxx = VAL-FS-001 항목 ID
- SOP-xxx = 해당 SOP 문서
- OQ-xxx = VAL-OQ-001 테스트 케이스 ID

---

## 2. 컴플라이언스 매트릭스

### § 1 — Risk Management (위험 관리)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §1 | 컴퓨터화 시스템의 전체 수명주기에 걸친 리스크 관리. 데이터 품질 및 무결성 보호를 위한 통제 수준은 리스크 기반으로 결정 | FMEA 방법론 적용. 21개 Critical 기능 식별 (RPN≥50 또는 S=5). 리스크 기반으로 OQ 테스트 우선순위 결정. ICH Q9 참조 | VAL-RA-001 RiskAssessment, ValidationPlan §4 Critical Functions |

### § 2 — Personnel (인력)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §2 | 컴퓨터화 시스템 사용·운영·유지에 관여하는 모든 인력은 교육받아야 함. 특히 사용자·개발자·기술 지원 인력 | 신규 계정 활성화 전 EDMS 교육 필수. 역할별 차별화 교육. 재교육 연 1회 + 시스템 변경 시. QA 분기별 교육 이수 현황 검토 | SOP-TRAINING-001, DS §4.2 training_assignments, FS-TRN-001~004 |

### § 3 — Suppliers and Service Providers (공급업체)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §3.1 | 시스템의 일부를 외부에서 조달하는 경우 공급업체 관리 필요. Quality Agreement 필요 | EDMS는 자체 개발(Bespoke). 사용 OSS 컴포넌트 목록 유지 (SBOM). 취약점 스캔(OWASP DC) 정기 실행 | ValidationPlan §GAMP 5 Cat 5, SOP-CICD-001 §5.3 |
| §3.2 | 호스팅·클라우드 서비스 사용 시 데이터 무결성·접근 통제 보장 | **On-Premise 사내 서버 운영** — 외부 클라우드 미사용. 사내 운영팀이 전체 스택 책임 | VAL-NET-001, DS §10.1 서버 구성 |
| §3.3 | 공급업체 audit 가능해야 함 | OSS 컴포넌트: 소스코드 공개(감사 가능). 사내 개발: 소스코드 완전 소유 | SOP-CICD-001 §5.1 소스 관리 |

### § 4 — Validation (검증)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §4.1 | 검증 문서는 데이터 무결성 및 기밀성에 영향을 미치는 중요 단계를 커버해야 함 | IQ/OQ/PQ 3단계 실행. URS→FS→DS→OQ→PQ 완전 추적성. 21개 Critical 기능 100% OQ 커버 | VAL-TM-001 TraceabilityMatrix, OQ_protocol |
| §4.2 | 변경 통제: 검증 시스템 변경 시 재검증 범위 결정 | SOP-CHANGE-001: Major/Minor/Emergency 분류. Impact Assessment로 재검증 범위 결정 | SOP-CHANGE-001 §5.3 |
| §4.3 | 마이그레이션: 기존 데이터를 새 시스템으로 이관 시 검증 필요 | PQ §3 마이그레이션 샘플링 QC. SHA-256 비교로 파일 무결성 검증 | PQ_protocol §3, PQ_report |

### § 5 — Data (데이터)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §5 | 컴퓨터화 시스템으로 생성·처리·저장되는 데이터는 ALCOA+ 원칙 준수 (Attributable, Legible, Contemporaneous, Original, Accurate + Complete, Consistent, Enduring, Available) | ALCOA+ 각 원칙별 시스템 대응 (SOP-DATA-INTEGRITY-001 §3): Attributable=사용자 ID+IP+타임스탬프, Legible=PDF 폰트 임베딩, Contemporaneous=NTP 서버 시각, Original=원본+PDF 둘 다 보관, Accurate=SHA-256+Envers | SOP-DATA-INTEGRITY-001, DS §8.1~8.5, FS-AUD-001~007 |

### § 6 — Accuracy Checks (정확성 검사)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §6 | 중요 데이터 수동 입력 시 정확성을 위한 추가 확인 제공 | 문서 마이그레이션 시 2인 교차 검증 절차. 채번 미리보기 제공(사용자 확인 후 실제 채번). 서명 모달에서 서명 의미·서명자 재확인 화면 표시 | PQ_protocol §3 (마이그레이션 QC), DS §6.3 numbering preview, DS §7.4 SignatureDialog |

### § 7 — Data Storage (데이터 저장)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §7.1 | 데이터는 정기적으로 백업되어야 함 | PostgreSQL: 매일 pg_basebackup + WAL 아카이빙. MinIO: Site Replication + 분기 콜드스토리지 스냅샷 | SOP-BACKUP-001 §5.1, DS §10.3 |
| §7.2 | 백업의 무결성·정확성·복구 가능성 검증 | 반기 1회 DR 복구 훈련 (RTO 4h 목표). 복구 데이터 SHA-256 비교 | SOP-DR-001, SOP-BACKUP-001 §5.4 |

### § 8 — Printouts (인쇄물)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §8 | 전자기록의 인쇄물이 원본과 동일해야 함. 인쇄 후 전자기록의 유효성 지속 | 모든 PDF에 "Controlled Document — Uncontrolled When Printed" 푸터 자동 삽입. 인쇄물은 비통제 사본으로 명확 표시. 원본 전자기록은 EDMS에서만 유효 | DS §5.4 WatermarkService |

### § 9 — Audit Trails (감사추적)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §9 | GMP 관련 데이터 생성·수정·삭제 시 audit trail 자동 기록. 일시, 운영자, 원본값 포함. 감사추적은 편집 불가 | audit_logs: INSERT-only (DB 역할 수준 REVOKE UPDATE/DELETE). Hibernate Envers: 엔티티 변경 자동 기록. SHA-256 해시체인 변조 감지. 일별 Merkle root WORM anchoring | DS §4.2 audit_logs, DS §4.3 DB 역할, DS §8.2~8.3, FS-AUD-001~007 |

### § 10 — Change and Configuration Management (변경·구성 관리)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §10 | 모든 시스템 변경 — 인프라, 소프트웨어, 데이터 — 은 통제되어야 함. 변경이 시스템의 검증 상태에 미치는 영향 평가 | SOP-CHANGE-001: Major(30BD)/Minor(10BD)/Emergency 경로. Impact Assessment로 재검증 범위 결정. SOP-CICD-001: Git 브랜치 전략, PR 리뷰, 파이프라인. IQ baseline Artifact SHA-256 관리 | SOP-CHANGE-001, SOP-CICD-001 |

### § 11 — Periodic Evaluation (정기 평가)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §11 | 시스템이 검증 상태를 유지하는지 정기적으로 평가. 현행 SOP, 편차, 인시던트 등 확인 | SOP-PERIODIC-001: 연 1회 시스템 정기 평가. SOP-AUDIT-TRAIL-001: 월별 감사추적 검토. 내부 감사: QA 분기 1회 | SOP-PERIODIC-001, SOP-AUDIT-TRAIL-001 |

### § 12 — Security (보안)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §12.1 | 물리적·논리적 접근 보안 | 사내 폐쇄망. 3차원 RBAC. 세션 쿠키 (HttpOnly+Secure+SameSite=Strict). TLS 1.2+. HSTS. 분기 계정 검토 | DS §9, VAL-NET-001, SOP-USER-001 |
| §12.2 | 무단 입력 방지 | @Valid Spring Validation. PreparedStatement 전용. 파일 업로드 MIME 화이트리스트 | DS §9.3 |
| §12.3 | 기록 비인가 변경 방지 | audit_logs INSERT-only. SHA-256 해시체인. MinIO Object Lock COMPLIANCE | DS §4.3, DS §8.2, DS §10.2 |
| §12.4 | 데이터 접근 제한 | 권한 매트릭스 (can_view, can_download, can_create 등). 기밀 문서 격리 | FS-ACC-001~004 |
| §12.5 | 비밀번호 보안 | BCrypt rounds=12. 8자 이상, 3종 이상 복잡도. 최근 5개 재사용 불가. 계정 공유 금지 | DS §8.4, SOP-USER-001 §5.5 |

### § 13 — Incident Management (인시던트 관리)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §13 | 시스템 보안 또는 데이터 무결성에 영향을 미치는 사고 보고·조사. 근본 원인 분석 및 재발 방지 | SOP-INCIDENT-001: 보안사고 분류(Critical/Major/Minor), NIST 4단계 대응, 24h 내부 보고, 외부 보고 기한. Root cause analysis + CAPA 연계 | SOP-INCIDENT-001 |

### § 14 — Electronic Signatures (전자서명)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §14 | 전자서명 — 서명자 성명, 일시, 의미 포함. 수기서명과 동일한 법적 효력 | SignatureManifest: signer_name, signed_at(NTP), meaning. SHA-256 해시체인으로 기록과 불분리 연결. 비밀번호 재인증으로 서명자 신원 확인. 세션 첫 서명 ID+PW 요구 | DS §4.2 signature_manifests, DS §8.1, FS-SIG-001~008, 21 CFR Part 11 §11.50/§11.200 |

### § 15 — Batch Release (배치 릴리즈)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §15 | QP가 전자기록·서명 기반으로 배치 출하 허가 가능 | **해당 없음 (N/A)**: EDMS는 R&D 연구소 중심 운영. GMP 배치 릴리즈는 Phase 1.c 이후 GMP 문서 카테고리 추가 시 적용 검토 | ValidationPlan §Phase 1.a 범위 |

### § 16 — Business Continuity (업무 연속성)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §16 | 시스템 장애 시 기록 가용성 및 업무 연속성을 위한 조치 | PostgreSQL Logical Replication → DR (RPO 1h). MinIO Site Replication (실시간). RTO 4h 목표. 반기 DR 복구 훈련. 백업 보관 1년 | SOP-DR-001, SOP-BACKUP-001, DS §10.3 |

### § 17 — Archiving (아카이빙)

| 항목 | 요구사항 | EDMS 대응 | 증거 |
|---|---|---|---|
| §17 | 전자기록 아카이빙 — 적절한 보관 기간 동안 완전성·가독성·정확성 유지 가능해야 함 | MinIO Object Lock COMPLIANCE 3650일(10년). 원본 파일·PDF 렌디션·감사 WORM 앵커 3종 모두 10년 보관. 계정 비활성화해도 과거 서명·감사로그 영구 보존 | DS §10.2 MinIO 버킷 구성, FS-AUD-007 |

---

## 3. 요약 — 조항별 준수 상태

| 조항 | 제목 | 준수 상태 | 비고 |
|---|---|---|---|
| §1 | Risk Management | ✅ 완전 준수 | FMEA, ICH Q9 |
| §2 | Personnel | ✅ 완전 준수 | SOP-TRAINING-001 |
| §3 | Suppliers | ✅ 완전 준수 | On-Premise 자체 개발 |
| §4 | Validation | ✅ 완전 준수 | GAMP 5 Cat 5 |
| §5 | Data | ✅ 완전 준수 | ALCOA+ |
| §6 | Accuracy Checks | ✅ 완전 준수 | 마이그레이션 교차검증 |
| §7 | Data Storage | ✅ 완전 준수 | 매일 백업 + DR |
| §8 | Printouts | ✅ 완전 준수 | "Uncontrolled" 푸터 |
| §9 | Audit Trails | ✅ 완전 준수 | INSERT-only + 해시체인 |
| §10 | Change Management | ✅ 완전 준수 | SOP-CHANGE-001 |
| §11 | Periodic Evaluation | ✅ 완전 준수 | SOP-PERIODIC-001 |
| §12 | Security | ✅ 완전 준수 | RBAC + TLS + 감사 |
| §13 | Incident Management | ✅ 완전 준수 | SOP-INCIDENT-001 |
| §14 | Electronic Signatures | ✅ 완전 준수 | 해시체인 + 재인증 |
| §15 | Batch Release | ➖ N/A | R&D 범위 외 |
| §16 | Business Continuity | ✅ 완전 준수 | RTO 4h / RPO 1h |
| §17 | Archiving | ✅ 완전 준수 | WORM 10년 |

---

## 4. 공급업체 목록 (§3 참조)

본 EDMS는 자체 개발(Bespoke)이며 아래 OSS 컴포넌트를 사용한다. 모든 컴포넌트는 사내 Nexus 미러에서 취득하며, 정기 취약점 스캔(OWASP Dependency-Check)을 수행한다.

| 컴포넌트 | 버전 | 라이선스 | 역할 |
|---|---|---|---|
| Spring Boot | 3.3.x | Apache 2.0 | 백엔드 프레임워크 |
| Spring Security | 6.x | Apache 2.0 | 인증·권한 |
| Hibernate Envers | 6.x | LGPL 2.1 | 엔티티 감사 |
| PostgreSQL | 16.x | PostgreSQL License | 데이터베이스 |
| Flyway | 10.x | Apache 2.0 | DB 마이그레이션 |
| MinIO Server | latest | AGPL 3.0 / Commercial | 오브젝트 스토리지 |
| MinIO Java SDK | latest | Apache 2.0 | S3 클라이언트 |
| LibreOffice | 7.x | MPL 2.0 | PDF 변환 |
| Apache PDFBox | 3.x | Apache 2.0 | PDF 워터마크 |
| Vue 3 | 3.x | MIT | 프론트엔드 |
| pdf.js | latest | Apache 2.0 | PDF 뷰어 |
| mecab-ko-dic | 현행 | Apache 2.0 | 한국어 형태소 |
| Nginx | 1.26 | BSD | 리버스 프록시 |

---

## 5. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 검토 전까지 최종 컴플라이언스 입장으로 사용할 수 없다.*
