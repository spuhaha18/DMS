# ADR 0001 — 보존정책은 테이블 기반, 연장 전용

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA/R&D |

## 맥락

코드에 하드코딩된 3650일(10년) 대신 SOP §6.2.2 Table 1에 정의된 시험 종류별 보존기간을 사용해야 한다. MinIO COMPLIANCE 잠금은 한번 걸면 단축할 수 없으므로, 상태 전이 시점에 "시작일 + N년"으로 정확히 늘리는 방식이 필요하다.

## 결정

1. **`research_project_types.retention_years`** 에서 보존기간 계산.
2. **ACTIVE 동안 임시 30년 락** — 과제 종료 전 조기 소멸 방지.
3. **상태 전이(APPROVED/TERMINATED) 시** `retention_extension_outbox` 큐에 등록 → 트랜잭션 커밋 후 워커가 MinIO `extendRetention()` 호출.
4. **단축 절대 불허** — `RetentionShortenedException` + MinIO COMPLIANCE 이중 가드.
5. **관리 권한** — `hasAnyRole('QA','ADMIN')`. V4 시드에 ADMIN 사용자만 있고 QA 사용자 시드 부재 → 두 역할 모두 허용해 IQ/PQ 진행 가능(M9에서 QA 시드 결정).
6. **영구 보존(`is_perpetual=TRUE`)** — `retention_years = NULL`, 계산 시 99년 캡 적용.

## 결과

- PDF 파이프라인 3곳(`PdfRenditionPipeline:174,267,438`) 하드코딩 제거.
- `AnchorService:79`의 3650은 ADR 0002(앵커 정책 외)로 유지.
- outbox 실패 5회 → dead-letter + `RETENTION_EXTENSION_FAILED` audit 기록.
