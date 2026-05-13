# ADR 0003 — MinIO 버킷 모드 GOVERNANCE → COMPLIANCE 전환

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA |

## 맥락

M7 cut-over 후 `documents-original-v2`와 `documents-rendition` 버킷은 GOVERNANCE 모드로 생성됨. GOVERNANCE는 `BypassGovernanceRetention` IAM 권한을 가진 관리자가 잠금을 단축·해제할 수 있어 GxP 규제 요건을 완전히 충족하지 못한다. COMPLIANCE 모드로 전환해야 한다.

## 문제

`MinioClientWrapper.ensureLockedBucket()`은 기존 버킷이 존재하면 `verifyLockConfiguration()`만 호출한다 — 즉, 모드 전환 기능이 없다. MinIO Object Lock 모드는 버킷 생성 시에만 설정 가능하므로 drop & recreate가 유일한 방법이다.

## 결정

**빈 버킷(`objects = 0`) 조건 하에 drop & recreate → COMPLIANCE + 30년(10950일).**

절차:
1. Pre-flight Step 0.2: 버킷 객체 0건 확인 (≥1이면 아래 Alternative 경로).
2. Pre-flight Step 0.3: IAM 정책/라이프사이클 백업.
3. `MinioClientWrapper.migrateBucketToCompliance(name, days)` 신규 메서드:
   - 객체 존재 → `IllegalStateException` 즉시 throw.
   - `removeBucket()` → `makeBucket(objectLock=true)` → `setObjectLockConfiguration(COMPLIANCE, 10950일)`.
4. 전환 후 Step 0.3 백업본으로 IAM/라이프사이클 재적용(수동 runbook).

## Alternative (버킷에 객체가 있을 경우)

GOVERNANCE 모드를 유지하되 IAM에서 `s3:BypassGovernanceRetention` 권한을 모든 사용자에서 제거. 실질적으로 COMPLIANCE와 동일한 단축 불가 효과. 이 경우 ADR을 수정하고 plan을 갱신한다.

## 결과

- `ensureBuckets()` 내 두 버킷 호출을 `migrateBucketToCompliance(name, 10950)`로 교체.
- 앵커 버킷은 기존 `ensureLockedBucket(name, COMPLIANCE, 3650)` 유지 (ADR 0002).
- 전환 후 MinIO 버킷 정보에서 `COMPLIANCE 10950일` 확인 (Verification step 2).
