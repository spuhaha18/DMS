-- V20: this_hash UNIQUE 제약 + 체인 무결성 뷰
-- M6 PR2: 중복 해시 방지(GxP §11.10) + v_signature_chain_integrity 감사 뷰

-- pgcrypto: encode(digest(...)) 사용을 위해 활성화
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. v2 row 중복 해시 사전 검사 (silent corruption 방지)
DO $$ DECLARE dup_count INT;
BEGIN
  SELECT COUNT(*) INTO dup_count FROM (
    SELECT this_hash FROM signature_manifests
    WHERE algorithm_version = 'v2'
    GROUP BY this_hash HAVING COUNT(*) > 1
  ) d;
  IF dup_count > 0 THEN
    RAISE EXCEPTION 'Duplicate this_hash found in v2 rows: % duplicates. Migration aborted.', dup_count;
  END IF;
END $$;

-- 2. UNIQUE 제약 추가
ALTER TABLE signature_manifests
  ADD CONSTRAINT uq_sigmf_this_hash UNIQUE (this_hash);

-- 3. 체인 무결성 뷰
--    prev_hash 가 직전 row 의 this_hash 와 불일치하는 링크(broken_links) 를 version_id 별 집계
--    첫 row 의 prev_hash 는 encode(digest('GENESIS','sha256'),'hex') 과 비교
--    SHA-256("GENESIS") = 901131d838b17aac0f7885b81e03cbdc9f5157a00343d30ab22083685ed1416a
--    주의: FILTER 절 안에 윈도우 함수 불가(PG 42P20) → CTE 로 window 먼저 계산
CREATE OR REPLACE VIEW v_signature_chain_integrity AS
WITH chain AS (
  SELECT
    version_id,
    prev_hash,
    COALESCE(
      LAG(this_hash) OVER (PARTITION BY version_id ORDER BY id),
      encode(digest('GENESIS', 'sha256'), 'hex')
    ) AS expected_prev_hash
  FROM signature_manifests
  WHERE algorithm_version = 'v2'
)
SELECT
  version_id,
  COUNT(*)                                              AS total,
  COUNT(*) FILTER (WHERE prev_hash != expected_prev_hash) AS broken_links
FROM chain
GROUP BY version_id;

COMMENT ON VIEW v_signature_chain_integrity IS
  'version_id 별 서명 체인 무결성 요약. broken_links > 0 이면 체인 단절 — GxP 21 CFR Part 11 §11.10(e) 감사 대상';
