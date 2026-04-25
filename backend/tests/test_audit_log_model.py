"""Tests for the audit_logs table.

The UPDATE/DELETE tests verify DB-level append enforcement.  They require the
app to be connecting as the 'dms' PostgreSQL role (which has had UPDATE and
DELETE revoked by the migration).  When running locally against a personal
postgres cluster (peer-auth user), those tests are skipped automatically.
"""
import pytest
from sqlalchemy import text


def _running_as_dms(db) -> bool:
    """Return True only when the DB session is authenticated as the 'dms' role."""
    row = db.execute(text("SELECT current_user")).scalar()
    return row == "dms"


def test_audit_log_insert(db):
    from app.models.audit_log import AuditLog
    row = AuditLog(actor="u1", action="TEST", target="t1", payload_json={"k": "v"})
    db.add(row)
    db.flush()  # get ID without committing; fixture rollback will undo
    assert row.id is not None


def test_audit_log_update_blocked(db):
    from app.models.audit_log import AuditLog

    if not _running_as_dms(db):
        pytest.skip("UPDATE-block test requires connecting as the 'dms' DB role")

    row = AuditLog(actor="u1", action="TEST", target="t1", payload_json={})
    db.add(row)
    db.flush()
    with pytest.raises(Exception):
        db.execute(text("UPDATE audit_logs SET action='TAMPER' WHERE id=:id"), {"id": row.id})


def test_audit_log_delete_blocked(db):
    from app.models.audit_log import AuditLog

    if not _running_as_dms(db):
        pytest.skip("DELETE-block test requires connecting as the 'dms' DB role")

    row = AuditLog(actor="u1", action="TEST", target="t1", payload_json={})
    db.add(row)
    db.flush()
    with pytest.raises(Exception):
        db.execute(text("DELETE FROM audit_logs WHERE id=:id"), {"id": row.id})
