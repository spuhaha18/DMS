# Changelog

All notable changes to this project will be documented in this file.

## [0.1.1.0] - 2026-05-14

### Added

- **M8 아키텍처 결정 기록 (ADR 0005–0011)**: Work Queue 통합 도메인, Outbox/DLQ 패턴, 위임(Delegation) 풀스택, 알림 보존 정책 등 7개 ADR 문서화
- **SOP-CHANGE-001-IMPACT-M8** (Major 등급): M8 전체 범위의 GxP 변경통제 영향평가서. 규제 영향(Part 11, Annex 11), OQ 케이스 33개 예약, 롤백 계획, 승인란 포함
- **DLQ 보존 정책**: `notification_dlq` 행 5년 보존 후 vacuum (ADR 0010)
- **Startup Reconciliation**: WorkQueueProjector 이벤트 소실 시 보상 전략 문서화 (ADR 0009)
- **교육·훈련 영향 평가**: 대상별 교육 내용 및 시점 명시 (SOP)
- **위임 만료 시 재귀속 정책**: 만료 후 대리인 OPEN 항목을 위임자에게 재귀속 (ADR 0011)

