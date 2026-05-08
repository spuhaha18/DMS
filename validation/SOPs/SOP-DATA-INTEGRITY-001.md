# SOP-DATA-INTEGRITY-001
# EDMS 데이터 무결성 정책 (Data Integrity Policy)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | SOP-DATA-INTEGRITY-001 |
| 버전 | 1.0 |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 승인 전 |
| 분류 | GxP SOP — 데이터 무결성 (ALCOA+) |
| 작성자 | TBD |
| 검토자 | TBD (QA) |
| 승인자 | TBD (QA Manager) |
| 다음 정기검토일 | 승인일 + 2년 |

---

## 1. 목적

본 SOP는 EDMS에서 생성·처리·저장되는 모든 GxP 데이터의 무결성을 보장하기 위한 정책과 절차를 정의한다. ALCOA+ 원칙을 기반으로 각 원칙이 시스템에서 어떻게 구현되는지 명확히 하고, 데이터 무결성 위협에 대한 통제 방안을 제시한다.

EU GMP Annex 11 §5 및 FDA Guidance for Industry — Data Integrity and Compliance with Drug CGMP (2018)에 부합한다.

---

## 2. 범위

본 SOP는 EDMS에 저장되는 모든 GxP 전자기록에 적용된다:
- 통제 문서 (SOP, Method, Specification, Form)
- 전자서명 매니페스트
- 감사로그
- 사용자 계정 및 권한 정보

---

## 3. ALCOA+ 정의 및 시스템 구현

| ALCOA+ 원칙 | 정의 | EDMS 구현 |
|---|---|---|
| **A**ttributable (귀속 가능) | 데이터를 생성·변경한 사람과 시각을 식별 가능해야 함 | 모든 API 호출: actor_user_id + client_ip + server_ts 기록. 익명 쓰기 불가 |
| **L**egible (가독성) | 데이터는 사람이 읽을 수 있어야 하며 원본 상태로 영구 보존 | LibreOffice→PDF 변환 시 한글 폰트(맑은고딕·나눔) 임베딩 필수. 워터마크 텍스트 선명도 유지 |
| **C**ontemporaneous (동시 기록) | 데이터는 발생 시점에 즉시 기록 | 모든 타임스탬프: 서버 NTP 시각 사용. 클라이언트 제공 시각 전면 무시. TIMESTAMPTZ 저장 |
| **O**riginal (원본) | 원본 데이터 또는 진정한 사본 | 원본 Office 파일 + 변환 PDF 둘 다 MinIO 보관. SHA-256으로 원본 식별. 후처리 파일을 원본으로 교체 불가 |
| **A**ccurate (정확성) | 데이터는 완전하고 오류 없이 측정값을 반영 | SHA-256 파일 무결성 검증. Hibernate Envers로 엔티티 변경 전/후 값 불변 기록 |
| **C**omplete (완전성) | 모든 관련 데이터 포함 (삭제된 관찰 포함) | 소프트 비활성화만 사용 (물리 삭제 없음). audit_logs 100% 이벤트 캡처. NULL 허용 필드 명시적 정의 |
| **C**onsistent (일관성) | 데이터와 관련 메타데이터가 단일 진실 공급원으로 일관성 유지 | PostgreSQL 단일 데이터베이스. FK 무결성 제약. 중복 저장 금지 (MinIO key는 DB가 관리) |
| **E**nduring (영속성) | 기록 보존 기간 동안 데이터 접근 가능 | MinIO Object Lock GOVERNANCE 10년 (원본·PDF), COMPLIANCE 10년 (감사 앵커). WAL 기반 DR |
| **A**vailable (가용성) | 데이터가 검토·감사 목적으로 접근 가능해야 함 | DR RTO 4h. SOP-DR-001 복구 훈련. 읽기 전용 API 경로 분리 (readonly_role) |

---

## 4. 책임

| 역할 | 책임 |
|---|---|
| 전체 사용자 | ALCOA+ 원칙 준수, 계정 공유 금지, 이상 즉시 신고 |
| 시스템 관리자 | 시스템 레벨 ALCOA+ 통제 유지, 정기 검토 |
| QA | 데이터 무결성 위험 평가, 감사 수행, 정책 업데이트 |
| 개발팀 | 설계 단계부터 ALCOA+ 원칙 내재화, 기술 통제 구현 |

---

## 5. 데이터 무결성 위협 및 통제 매트릭스

| 위협 | 설명 | 통제 수단 | 탐지 방법 |
|---|---|---|---|
| **계정 공유** | 복수 사용자가 동일 계정 사용 → 귀속 불가 | 계정 공유 금지 정책(SOP-USER-001 §5.5), BCrypt 비밀번호 개인 관리, 비밀번호 타인 미공유 | 동시 세션 감지, 동일 계정 다중 IP 로그인 alert |
| **시간 조작** | 클라이언트 시간 조작으로 서명·로그 시간 위조 | 서버 NTP 시각 전용 사용. 클라이언트 시각 입력 API 거부. NTP 드리프트 >1s 알림 | SOP-AUDIT-TRAIL-001 §5.2 시간 이상 감지 |
| **직접 DB 수정** | app_role로 audit_logs/signature_manifests UPDATE/DELETE 시도 | DB 역할 분리: `REVOKE UPDATE, DELETE ON audit_logs FROM app_role`. 정기 권한 검증 | OQ-AUD-001~005 (DB 직접 UPDATE 시도 → 거부 확인) |
| **우회 입력** | API를 거치지 않고 DB에 직접 INSERT하여 감사추적 없이 데이터 생성 | DB 연결은 app_role/audit_role만. 연결 IP 제한. INSERT-only 역할(audit_role)은 감사 테이블만 접근 | SOP-AUDIT-TRAIL-001 Admin 행위 100% 검토 |
| **백도어 접근** | 운영 서버 직접 접근으로 파일 조작 | OS 레벨 접근 로그. MinIO Object Lock (삭제 불가). 최소 권한 운영자 계정 | 운영 서버 syslog 모니터링, SOP-INCIDENT-001 |
| **서명 위조** | 타인 서명 생성 (비밀번호 탈취) | 서명 시 비밀번호 재인증. 5회 실패 시 잠금. SHA-256 해시체인으로 서명 순서·서명자 변조 불가 | SOP-AUDIT-TRAIL-001 §5.2 서명 샘플 검토 |
| **마이그레이션 오류** | 레거시 문서 이관 시 메타데이터 손실·변형 | 마이그레이션 SHA-256 비교. 2인 교차 검증. PQ 프로토콜 §3 | PQ 마이그레이션 QC |
| **소프트웨어 버그** | 코드 결함으로 데이터 누락·오염 | 단위/통합테스트 (Testcontainers). SAST. OQ 전 기능 검증. SOP-CHANGE-001 변경 통제 | OQ 전체 102개 케이스 |

---

## 6. 사용자 수칙

모든 EDMS 사용자는 다음 수칙을 준수해야 한다. 위반 시 SOP-DEVIATION-001에 따라 처리한다.

| 번호 | 수칙 | 근거 |
|---|---|---|
| 1 | **계정 공유 절대 금지**: 내 계정과 비밀번호를 동료에게도 알려주지 않는다 | ALCOA+ Attributable. Part 11 §11.100(a) |
| 2 | **비밀번호 보안**: 비밀번호를 화면에 노출하거나 메모지에 적어두지 않는다 | Part 11 §11.300 |
| 3 | **자리 비울 때 잠금**: 자리 이탈 시 화면 잠금 또는 로그아웃 | 비인가 접근 방지 |
| 4 | **서명 의미 준수**: 전자서명 시 실제 업무 행위(검토 완료, 승인 등)가 완료된 후에만 서명한다. 형식적 서명 금지 | ALCOA+ Contemporaneous, Accurate |
| 5 | **이상 즉시 신고**: 비밀번호 노출·계정 탈취 의심 시 즉시 시스템 관리자에게 신고 | SOP-INCIDENT-001 §5.1 |
| 6 | **원본 데이터 훼손 금지**: 문서 원본 파일을 외부 도구로 편집 후 재업로드하지 않는다 | ALCOA+ Original |
| 7 | **동시 로그인 주의**: 같은 계정으로 여러 PC에서 동시 작업하지 않는다 | 귀속 혼란 방지 |

---

## 7. 정기 데이터 무결성 감사

**주기**: 분기 1회  
**수행자**: QA (시스템 관리자 협력)  
**목적**: ALCOA+ 원칙 준수 여부 샘플 확인

**7.1 감사 항목**

| 항목 | 확인 방법 |
|---|---|
| 감사추적 완전성 | audit_logs 이벤트 연속성 확인 (SOP-AUDIT-TRAIL-001 §5.3 연계) |
| 타임스탬프 정확성 | NTP chrony 동기화 상태 확인 (`chronyc tracking`) |
| DB 역할 권한 | REVOKE가 유지되는지 확인 (`\dp audit_logs` in psql) |
| 파일 무결성 | 무작위 20건 MinIO → DB SHA-256 비교 |
| 서명 귀속 | 선택 서명 10건 → 해당 사용자 워크플로 참여 여부 확인 |
| 사용자 행위 일관성 | 비업무시간 이벤트·이상 패턴 없는지 확인 |

**7.2 감사 결과 기록**

감사 결과는 EDMS 내 QA 폴더에 저장하며, QA Manager 서명으로 완료 처리한다. 중대한 발견 사항은 SOP-DEVIATION-001 또는 SOP-INCIDENT-001을 발동한다.

---

## 8. 교육

신규 사용자 교육에 데이터 무결성 원칙 강의를 포함한다. 본 SOP는 SOP-TRAINING-001에 따라 교육 커리큘럼에 포함된다.

---

## 9. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-DS-001 | §4.2~4.3 DB 설계, §8.1~8.5 핵심 알고리즘 |
| SOP-AUDIT-TRAIL-001 | 감사추적 검토 절차 |
| SOP-INCIDENT-001 | 보안사고 대응 절차 |
| SOP-DEVIATION-001 | 운영 일탈 관리 절차 |
| SOP-TRAINING-001 | 사용자 교육 절차 |
| SOP-USER-001 | §5.5 비밀번호·계정 수칙 |
| EU GMP Annex 11 | §5 Data |
| FDA Guidance | Data Integrity and Compliance with Drug CGMP (2018) |
| MHRA Guidance | GxP Data Integrity Guidance and Definitions (2018) |

---

## 10. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 1.0 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 승인 전까지 운영 정책으로 사용할 수 없다.*
