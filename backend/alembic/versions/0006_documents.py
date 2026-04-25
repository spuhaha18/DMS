"""documents table

Revision ID: 0006_documents
Revises: c4fe0396e711
Create Date: 2026-04-26 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '0006_documents'
down_revision: Union[str, Sequence[str], None] = ('c4fe0396e711', 'e29d15704297')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table(
        'documents',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('doc_number', sa.String(length=64), nullable=False),
        sa.Column('revision', sa.String(length=8), nullable=True),
        sa.Column('document_type_id', sa.Integer(), nullable=False),
        sa.Column('project_id', sa.Integer(), nullable=False),
        sa.Column('author_username', sa.String(length=64), nullable=False),
        sa.Column('parent_document_id', sa.Integer(), nullable=True),
        sa.Column('relation_type', sa.String(length=16), nullable=True),
        sa.Column('title', sa.String(length=500), nullable=False),
        sa.Column('effective_status', sa.String(length=16), nullable=False, server_default='Draft'),
        sa.Column('effective_date', sa.Date(), nullable=True),
        sa.Column('expiration_date', sa.Date(), nullable=True),
        sa.Column('security_level', sa.String(length=16), nullable=False, server_default='Confidential'),
        sa.Column('template_id', sa.Integer(), nullable=False),
        sa.Column('template_version', sa.String(length=32), nullable=False),
        sa.Column('source_docx_key', sa.String(length=256), nullable=False),
        sa.Column('final_pdf_object_key', sa.String(length=256), nullable=True),
        sa.Column('final_pdf_sha256', sa.String(length=64), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
        sa.ForeignKeyConstraint(['document_type_id'], ['document_types.id'], ),
        sa.ForeignKeyConstraint(['parent_document_id'], ['documents.id'], ),
        sa.ForeignKeyConstraint(['project_id'], ['projects.id'], ),
        sa.ForeignKeyConstraint(['template_id'], ['templates.id'], ),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('doc_number'),
    )
    op.create_index(op.f('ix_documents_document_type_id'), 'documents', ['document_type_id'], unique=False)
    op.create_index(op.f('ix_documents_project_id'), 'documents', ['project_id'], unique=False)
    op.create_index(op.f('ix_documents_author_username'), 'documents', ['author_username'], unique=False)


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_index(op.f('ix_documents_author_username'), table_name='documents')
    op.drop_index(op.f('ix_documents_project_id'), table_name='documents')
    op.drop_index(op.f('ix_documents_document_type_id'), table_name='documents')
    op.drop_table('documents')
