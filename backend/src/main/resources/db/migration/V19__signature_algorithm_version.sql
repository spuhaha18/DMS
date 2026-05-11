-- V19: algorithm_version 컬럼 추가 (legacy v1 동결, 신규 v2)
-- M6 PR1: canonical_payload 알고리즘 교체 시 기존 row 의 GxP 감사성 보존

ALTER TABLE signature_manifests
  ADD COLUMN algorithm_version VARCHAR(10) NOT NULL DEFAULT 'v1';

COMMENT ON COLUMN signature_manifests.algorithm_version IS
  'canonical_payload 직렬화 알고리즘 버전. v1=M4 stub(JSON), v2=M6 DS §8.1 9-필드 NFC+escape. 기존 row 는 v1 동결, M6 이후 신규 = v2';
