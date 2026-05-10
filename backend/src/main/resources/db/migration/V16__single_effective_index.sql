-- 사전 검증: 기존 EFFECTIVE 버전 중복 없는지 확인
DO $$
BEGIN
    IF EXISTS (
        SELECT document_id FROM document_versions WHERE state = 'EFFECTIVE'
        GROUP BY document_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V16 사전 조건 실패: 동일 document_id에 EFFECTIVE 버전이 2개 이상 존재합니다';
    END IF;
END $$;

-- 한 document_id 당 EFFECTIVE 버전은 최대 1개
CREATE UNIQUE INDEX uq_one_effective_version
    ON document_versions(document_id) WHERE state = 'EFFECTIVE';

-- in-flight 상태 버전 추적 인덱스
CREATE INDEX idx_dv_inflight ON document_versions(document_id)
    WHERE state IN ('DRAFT','UNDER_REVIEW','UNDER_APPROVAL','UNDER_REVISION');
