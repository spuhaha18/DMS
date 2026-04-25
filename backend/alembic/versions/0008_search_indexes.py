"""Add FTS search indexes to documents

Revision ID: 0008_search_indexes
Revises: 0007_approvals
Create Date: 2026-04-26
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = '0008_search_indexes'
down_revision = '0007_approvals'
branch_labels = None
depends_on = None


def upgrade():
    op.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")
    op.add_column("documents",
        sa.Column("search_tsv", postgresql.TSVECTOR, nullable=True))
    op.execute("""
        UPDATE documents SET search_tsv =
          setweight(to_tsvector('simple', coalesce(doc_number,'')), 'A') ||
          setweight(to_tsvector('simple', coalesce(title,'')), 'B') ||
          setweight(to_tsvector('simple', coalesce(author_username,'')), 'C')
    """)
    op.execute("CREATE INDEX idx_documents_search_tsv ON documents USING GIN (search_tsv)")
    op.execute("CREATE INDEX idx_documents_doc_number_trgm ON documents USING GIN (doc_number gin_trgm_ops)")
    op.execute("CREATE INDEX idx_documents_title_trgm ON documents USING GIN (title gin_trgm_ops)")
    op.execute("""
        CREATE OR REPLACE FUNCTION documents_tsv_update() RETURNS trigger AS $$
        BEGIN
          NEW.search_tsv :=
            setweight(to_tsvector('simple', coalesce(NEW.doc_number,'')), 'A') ||
            setweight(to_tsvector('simple', coalesce(NEW.title,'')), 'B') ||
            setweight(to_tsvector('simple', coalesce(NEW.author_username,'')), 'C');
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
        CREATE TRIGGER trg_documents_tsv BEFORE INSERT OR UPDATE ON documents
          FOR EACH ROW EXECUTE FUNCTION documents_tsv_update()
    """)


def downgrade():
    op.execute("DROP TRIGGER IF EXISTS trg_documents_tsv ON documents")
    op.execute("DROP FUNCTION IF EXISTS documents_tsv_update()")
    op.execute("DROP INDEX IF EXISTS idx_documents_title_trgm")
    op.execute("DROP INDEX IF EXISTS idx_documents_doc_number_trgm")
    op.execute("DROP INDEX IF EXISTS idx_documents_search_tsv")
    op.drop_column("documents", "search_tsv")
