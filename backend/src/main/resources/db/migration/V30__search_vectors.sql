-- V30__search_vectors.sql
-- content_summary 컬럼이 V9에 없으므로 먼저 추가
ALTER TABLE document_versions ADD COLUMN IF NOT EXISTS content_summary TEXT;

-- already added in V9; IF NOT EXISTS is a safety guard
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS search_vector TSVECTOR;
-- already added in V9; IF NOT EXISTS is a safety guard
ALTER TABLE document_versions
    ADD COLUMN IF NOT EXISTS search_vector TSVECTOR;

CREATE INDEX IF NOT EXISTS idx_documents_search
    ON documents USING GIN(search_vector);

CREATE INDEX IF NOT EXISTS idx_docversions_search
    ON document_versions USING GIN(search_vector);

-- simple config 사용 (mecab-ko 설치 전 안전한 폴백)
CREATE OR REPLACE FUNCTION update_document_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('simple', coalesce(NEW.doc_number, '')) ||
        to_tsvector('simple', coalesce(NEW.title, ''));
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION update_docversion_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('simple', coalesce(NEW.title, '')) ||
        to_tsvector('simple', coalesce(NEW.content_summary, ''));
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_documents_sv ON documents;
CREATE TRIGGER trg_documents_sv
    BEFORE INSERT OR UPDATE OF doc_number, title
    ON documents
    FOR EACH ROW EXECUTE FUNCTION update_document_search_vector();

DROP TRIGGER IF EXISTS trg_docversions_sv ON document_versions;
CREATE TRIGGER trg_docversions_sv
    BEFORE INSERT OR UPDATE OF title, content_summary
    ON document_versions
    FOR EACH ROW EXECUTE FUNCTION update_docversion_search_vector();

-- 기존 데이터 백필
UPDATE documents SET search_vector =
    to_tsvector('simple', coalesce(doc_number, '')) ||
    to_tsvector('simple', coalesce(title, ''))
WHERE search_vector IS NULL;

UPDATE document_versions SET search_vector =
    to_tsvector('simple', coalesce(title, '')) ||
    to_tsvector('simple', coalesce(content_summary, ''))
WHERE search_vector IS NULL;
