-- V22: v_signature_chain_integrity view 재작성 — algorithm_version 무관
-- M7 PR4: v3 row 도 chain 검증에 포함

DROP VIEW IF EXISTS v_signature_chain_integrity;

CREATE VIEW v_signature_chain_integrity AS
WITH chain AS (
  SELECT
    version_id,
    id,
    algorithm_version,
    prev_hash,
    COALESCE(
      LAG(this_hash) OVER (PARTITION BY version_id ORDER BY id),
      encode(digest('GENESIS', 'sha256'), 'hex')
    ) AS expected_prev_hash
  FROM signature_manifests
)
SELECT
  version_id,
  COUNT(*) AS total,
  COUNT(*) FILTER (WHERE prev_hash != expected_prev_hash) AS broken_links,
  COUNT(*) FILTER (WHERE algorithm_version = 'v1') AS v1_rows,
  COUNT(*) FILTER (WHERE algorithm_version = 'v2') AS v2_rows,
  COUNT(*) FILTER (WHERE algorithm_version = 'v3') AS v3_rows
FROM chain
GROUP BY version_id;

COMMENT ON VIEW v_signature_chain_integrity IS
  'M7 V22: algorithm_version 무관 chain 검증. v1/v2/v3 row 모두 LAG 기반 prev_hash 검증.';

-- ShedLock 테이블 (EffectiveWatermarkScheduler leader election)
CREATE TABLE IF NOT EXISTS shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at  TIMESTAMP(3) NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
