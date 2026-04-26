"""Replace single-column doc_number unique with composite (doc_number, revision)

Revision ID: 0009_fix_doc_number_unique
Revises: 0008_search_indexes
Create Date: 2026-04-27 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '0009_fix_doc_number_unique'
down_revision: Union[str, Sequence[str], None] = '0008_search_indexes'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.drop_constraint('documents_doc_number_key', 'documents', type_='unique')
    op.create_unique_constraint(
        'uq_documents_doc_number_revision',
        'documents',
        ['doc_number', 'revision'],
    )


def downgrade() -> None:
    op.drop_constraint('uq_documents_doc_number_revision', 'documents', type_='unique')
    op.create_unique_constraint('documents_doc_number_key', 'documents', ['doc_number'])
