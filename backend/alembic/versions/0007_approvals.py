"""approvals and approval_templates tables

Revision ID: 0007_approvals
Revises: 0006_documents
Create Date: 2026-04-26 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '0007_approvals'
down_revision: Union[str, Sequence[str], None] = '0006_documents'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table(
        'approval_templates',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('doc_type_id', sa.Integer(), nullable=False),
        sa.Column('name', sa.String(length=200), nullable=False),
        sa.Column('config', sa.JSON(), nullable=False),
        sa.ForeignKeyConstraint(['doc_type_id'], ['document_types.id'], ),
        sa.PrimaryKeyConstraint('id'),
    )
    op.create_index(op.f('ix_approval_templates_doc_type_id'), 'approval_templates', ['doc_type_id'], unique=False)

    op.create_table(
        'approvals',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('document_id', sa.Integer(), nullable=False),
        sa.Column('step_order', sa.Integer(), nullable=False),
        sa.Column('role', sa.String(length=16), nullable=False),
        sa.Column('assigned_username', sa.String(length=64), nullable=False),
        sa.Column('status', sa.String(length=16), nullable=False, server_default='Pending'),
        sa.Column('decided_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('comment', sa.Text(), nullable=True),
        sa.Column('signature_meaning', sa.String(length=64), nullable=True),
        sa.ForeignKeyConstraint(['document_id'], ['documents.id'], ),
        sa.PrimaryKeyConstraint('id'),
    )
    op.create_index(op.f('ix_approvals_document_id'), 'approvals', ['document_id'], unique=False)
    op.create_index(op.f('ix_approvals_assigned_username'), 'approvals', ['assigned_username'], unique=False)


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_index(op.f('ix_approvals_assigned_username'), table_name='approvals')
    op.drop_index(op.f('ix_approvals_document_id'), table_name='approvals')
    op.drop_table('approvals')
    op.drop_index(op.f('ix_approval_templates_doc_type_id'), table_name='approval_templates')
    op.drop_table('approval_templates')
