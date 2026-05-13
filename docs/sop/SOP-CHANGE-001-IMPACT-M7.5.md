# SOP-CHANGE-001 변경통제 영향평가서 — M7.5 연구과제 마스터 + 보존정책화

| 문서번호 | SOP-CHANGE-001-IMPACT-M7.5 |
|---------|--------------------------|
| 버전 | 1.0 |
| 작성일 | 2026-05-13 |
| 작성자 | IT 개발팀 |
| 변경 분류 | **Major** |
| 상태 | 초안 — 3명 서명 후 PR1 착수 가능 |

---

## 1. 변경 식별

### 1.1 변경 유형

**Major** — 신규 마스터 테이블 2개 신설, MinIO 버킷 잠금 모드 변경(GOVERNANCE → COMPLIANCE), 보존기간 계산 로직 교체, 채번 범위 코드 리네임.

### 1.2 변경 제목

Table 1 적용 GxP 산출물(시방서·시험기록 등)의 연구과제 마스터 관리 + 보존기간 SOP 표 기반 자동 계산 도입

### 1.3 변경 번호

M7.5

---

## 2. 변경 범위

### 2.1 적용 대상 문서

`document_categories.uses_table1 = TRUE`로 표시된 카테고리에 속한 문서만 해당.

현재 지정 카테고리(QA 합의 후 V25 UPDATE):
- SPEC(시방서) — 대상 예정
- 기타 카테고리 — QA 합의 후 결정

### 2.2 제외 대상

- SOP, 품질매뉴얼, 지침서 등 회사 운영문서(`uses_table1 = FALSE`) — **영향 없음**
- `documents-original` v1 레거시 버킷 — **이번 범위 외** (M7.6 후보)
- Merkle 앵커 버킷(`documents-anchors`) — **정책 외 고정 10년** (ADR 0002)

### 2.3 변경 내용 요약

| 영역 | Before | After |
|------|--------|-------|
| 연구과제 식별 | `documents.project_code` 자유 텍스트 | `research_projects` 마스터 FK |
| 보존기간 계산 | 코드 하드코딩 3650일(10년) | SOP §6.2.2 Table 1 기반 계산 |
| MinIO 잠금 모드 | GOVERNANCE(단축 허용) | COMPLIANCE(단축 절대 불가) |
| 잠금 기간 | 10년 고정 | ACTIVE: 임시 30년, 상태 전이 후: 시작일+N년 |
| 채번 범위 | PER_PRODUCT | PER_PROJECT (PER_PRODUCT alias 6개월 유지) |

---

## 3. 영향 평가

### 3.1 검증(V&V) 영향

| 항목 | 영향 | 조치 |
|------|------|------|
| IQ(설치 적격성) | 신규 테이블 V23/V24, 신규 MinIO 버킷 모드 | IQ 신규 체크리스트 추가 |
| OQ(운영 적격성) | API 신규 6개 엔드포인트, outbox 워커 | OQ 재실행 — 기존 시나리오 + 신규 10개 |
| PQ(성능 적격성) | outbox 동시성, 대량 문서 페이지네이션 | PQ 신규 시나리오: 200건 이상 과제 전이 |

### 3.2 데이터 영향

- `documents.project_code` 컬럼: VARCHAR(100) → VARCHAR(50) FK. 기존 값은 `project_code_legacy` 컬럼으로 백업 후 NULL로 전환. **운영 데이터 0건 전제(Pre-flight Gate 확인).**
- MinIO 버킷 drop & recreate: 운영 객체 0건 전제. 객체 존재 시 작업 중단.

### 3.3 규제 영향

| 규정 | 영향 |
|------|------|
| 21 CFR Part 11 | COMPLIANCE 잠금 강화 — 단축 불가 이중 가드 |
| EU GMP Annex 11 | 감사추적(audit_logs) 변경 없음 — append-only 유지 |
| SOP §6.2.2 Table 1 | 이번 변경의 직접 근거 |

---

## 4. 위험 평가

| 위험 | 등급 | 대책 |
|------|------|------|
| COMPLIANCE 잠금 후 단축 시도 | **높음** | RetentionShortenedException 차단 + MinIO COMPLIANCE 이중 가드 |
| outbox 워커 5회 재시도 실패 | **중간** | dead-letter 기록 + audit RETENTION_EXTENSION_FAILED 알림 |
| `project_code_legacy` 데이터 유실 | **중간** | V23에서 별도 컬럼 백업, 롤백 경로 명시 |
| feature flag 미전환 | **낮음** | 30년 fallback 유지 — 보존 부족 위험 없음 |
| PER_PRODUCT alias 기간 내 제거 | **낮음** | V26(6개월 후) 이전 제거 금지 |

---

## 5. 검증 계획

### 5.1 OQ 재실행 항목

기존 OQ 시나리오 전체 재실행 + 신규:
1. 연구과제 등록 → ACTIVE 상태 확인
2. 문서 연결(FK) 정상 동작
3. APPROVE 전이 → outbox PENDING → SUCCESS
4. TERMINATE 전이 → outbox PENDING → SUCCESS
5. 단축 시도 차단 확인
6. READER 권한 403 확인
7. 동시 워커 2개 — 중복 처리 0건
8. feature flag false → 30년 fallback
9. feature flag true → SOP Table 1 기반 계산
10. 채번 PER_PROJECT 정상 동작

### 5.2 PQ 신규 시나리오

- 과제 연결 문서 200건 이상 → outbox 전체 처리 완료 ≤ 60초
- 동시 APPROVE 요청 2개 → 중복 없음

---

## 6. 롤백 계획

| 단계 | 롤백 방법 | 비가역 여부 |
|------|-----------|------------|
| V23 마이그레이션 | `DROP TABLE research_projects CASCADE` + V23 reverse | 가역 |
| V24 마이그레이션 | `DROP TABLE retention_extension_outbox` | 가역 |
| MinIO COMPLIANCE 전환 | **비가역** — 잠금 기간 단축 불가. 단, 새 객체 업로드 전 전환 시 버킷 drop & recreate 재시도 가능 | **비가역(객체 업로드 후)** |
| feature flag | `retention.feature.project-based-enabled: false`로 복원 + 재배포 | 가역 |
| 코드 변경 | PR 리버트 | 가역 |

---

## 7. 승인란

본 영향평가서는 아래 서명자 3명의 승인 후 PR1 구현 착수를 허가한다.

| 역할 | 성명 | 서명 | 날짜 |
|------|------|------|------|
| QA 매니저 | | | |
| R&D 부서장 | | | |
| IT 책임자 | | | |

> **주의:** 이 문서에 서명 전 PR1 이후 코드 작업을 시작할 수 없습니다. (SOP-CHANGE-001 §4.3)
