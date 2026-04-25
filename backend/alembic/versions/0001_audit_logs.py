"""audit_logs — append-only table with DB-level REVOKE

Revision ID: d247d4fc9f8a
Revises:
Create Date: 2026-04-26 00:13:16.426253

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'd247d4fc9f8a'
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table(
        'audit_logs',
        sa.Column('id', sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column('actor', sa.String(length=64), nullable=False),
        sa.Column('action', sa.String(length=64), nullable=False),
        sa.Column('target', sa.String(length=128), nullable=False),
        sa.Column('payload_json', sa.JSON(), nullable=False),
        sa.Column('ts', sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint('id'),
    )
    op.create_index(op.f('ix_audit_logs_action'), 'audit_logs', ['action'], unique=False)
    op.create_index(op.f('ix_audit_logs_actor'), 'audit_logs', ['actor'], unique=False)
    op.create_index(op.f('ix_audit_logs_target'), 'audit_logs', ['target'], unique=False)
    op.create_index(op.f('ix_audit_logs_ts'), 'audit_logs', ['ts'], unique=False)

    # Enforce append-only at DB level: revoke UPDATE/DELETE from the app user.
    # INSERT is still permitted; SELECT is unchanged.
    # Conditional: only execute if the 'dms' role exists (avoids failure in
    # local dev environments that connect as a different OS user via peer auth).
    op.execute(
        """
        DO $$
        BEGIN
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dms') THEN
                REVOKE UPDATE, DELETE ON audit_logs FROM dms;
            END IF;
        END
        $$;
        """
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_index(op.f('ix_audit_logs_ts'), table_name='audit_logs')
    op.drop_index(op.f('ix_audit_logs_target'), table_name='audit_logs')
    op.drop_index(op.f('ix_audit_logs_actor'), table_name='audit_logs')
    op.drop_index(op.f('ix_audit_logs_action'), table_name='audit_logs')
    op.drop_table('audit_logs')
