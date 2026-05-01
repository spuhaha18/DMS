import hashlib
import json
from typing import Any

from django.db import connection, transaction

from audit.models import AuditEvent


def _canonical_payload(event: AuditEvent, prev_hash: str) -> str:
    payload: dict[str, Any] = {
        "actor_id": event.actor_id,
        "event_type": event.event_type,
        "object_type": event.object_type,
        "object_id": event.object_id,
        "before": event.before,
        "after": event.after,
        "reason": event.reason,
        "prev_hash": prev_hash,
    }
    return json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)


@transaction.atomic
def append_event(*, actor, event_type: str, object_type: str, object_id: str, before=None, after=None, reason: str = "") -> AuditEvent:
    # select_for_update serializes concurrent appends on PostgreSQL; SQLite uses
    # file-level write locking so the race cannot occur there.
    qs = AuditEvent.objects.order_by("-id")
    if connection.vendor != "sqlite":
        qs = qs.select_for_update()
    previous = qs.first()
    prev_hash = previous.event_hash if previous else ""
    event = AuditEvent(
        actor=actor,
        event_type=event_type,
        object_type=object_type,
        object_id=str(object_id),
        before=before or {},
        after=after or {},
        reason=reason,
        prev_hash=prev_hash,
    )
    event.event_hash = hashlib.sha256(_canonical_payload(event, prev_hash).encode("utf-8")).hexdigest()
    event.save()
    return event


def validate_hash_chain() -> list[str]:
    errors: list[str] = []
    previous_hash = ""
    for event in AuditEvent.objects.order_by("id"):
        expected = hashlib.sha256(_canonical_payload(event, previous_hash).encode("utf-8")).hexdigest()
        if event.prev_hash != previous_hash:
            errors.append(f"event {event.id}: prev_hash mismatch")
        if event.event_hash != expected:
            errors.append(f"event {event.id}: event_hash mismatch")
        previous_hash = event.event_hash
    return errors
