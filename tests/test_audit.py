import pytest
from django.contrib.auth.models import User

from audit.models import AuditEvent
from audit.services import append_event, validate_hash_chain


@pytest.mark.django_db
def test_append_event_hash_chains_events():
    user = User.objects.create_user("qa", password="pw")
    first = append_event(actor=user, event_type="project.created", object_type="ProjectCode", object_id="P1", after={"code": "P1"}, reason="initial setup")
    second = append_event(actor=user, event_type="document.reserved", object_type="Document", object_id="D1", after={"number": "P1-AM-0001"}, reason="registration")

    assert first.prev_hash == ""
    assert len(first.event_hash) == 64
    assert second.prev_hash == first.event_hash
    assert validate_hash_chain() == []


@pytest.mark.django_db
def test_audit_event_update_is_blocked():
    event = append_event(actor=None, event_type="system.test", object_type="System", object_id="1", after={"ok": True}, reason="test")
    event.reason = "tamper"

    with pytest.raises(ValueError, match="append-only"):
        event.save()
