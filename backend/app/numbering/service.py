import re
from datetime import datetime
from sqlalchemy import text
from sqlalchemy.orm import Session
from app.models.numbering import NumberingSequence
from app.models.master import Project, DocumentType

LOCK_BASE = 0xC0FFEE_00000000


def _period_for(pattern: str, now: datetime) -> str:
    if "{year}" in pattern:
        return str(now.year)
    if "{ym}" in pattern:
        return f"{now.year}-{now.month:02d}"
    return "all"


def assign_number(db: Session, project_id: int, doc_type_id: int, *, now: datetime | None = None) -> str:
    """Assign a unique sequential document number within a transaction.

    Uses advisory lock to serialize concurrent access to the same (project, doc_type, period) sequence.
    Must be called within an active transaction; caller is responsible for commit.
    """
    now = now or datetime.utcnow()
    dt = db.get(DocumentType, doc_type_id)
    pj = db.get(Project, project_id)
    if not dt or not pj:
        raise ValueError(f"Project {project_id} or DocumentType {doc_type_id} not found")

    period = _period_for(dt.numbering_pattern, now)

    # Advisory lock prevents duplicate sequence numbers under concurrency
    lock_key = int(LOCK_BASE | ((project_id * 1_000_003 + doc_type_id) & 0xFFFFFFFF))
    db.execute(text("SELECT pg_advisory_xact_lock(:k)"), {"k": lock_key})

    seq = (
        db.query(NumberingSequence)
        .filter_by(project_id=project_id, doc_type_id=doc_type_id, period=period)
        .with_for_update()
        .first()
    )
    if not seq:
        seq = NumberingSequence(project_id=project_id, doc_type_id=doc_type_id,
                                period=period, next_seq=1)
        db.add(seq)
        db.flush()

    n = seq.next_seq
    seq.next_seq += 1

    return dt.numbering_pattern.format(
        project_code=pj.code,
        doc_type=dt.code,
        year=now.year,
        ym=f"{now.year}-{now.month:02d}",
        seq=n,
    )
