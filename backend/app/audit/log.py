from app.models.audit_log import AuditLog


def log_action(db, *, actor: str, action: str, target: str, payload: dict | None = None) -> AuditLog:
    """Insert an audit log entry. Never call db.commit() here — caller manages transaction."""
    row = AuditLog(
        actor=actor,
        action=action,
        target=target,
        payload_json=payload or {},
    )
    db.add(row)
    db.flush()  # assigns ID without committing
    return row
