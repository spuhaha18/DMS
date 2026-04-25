from datetime import datetime
from app.audit.log import log_action


def test_log_action_inserts_row(db):
    row = log_action(db, actor="alice", action="LOGIN", target="alice", payload={"ip": "10.0.0.1"})
    assert row.id is not None
    assert row.actor == "alice"
    assert row.action == "LOGIN"
    assert row.payload_json == {"ip": "10.0.0.1"}
    assert isinstance(row.ts, datetime)


def test_log_action_default_payload(db):
    row = log_action(db, actor="bob", action="TEST", target="resource:1")
    assert row.payload_json == {}


def test_log_action_multiple_in_one_transaction(db):
    r1 = log_action(db, actor="u1", action="A", target="t1")
    r2 = log_action(db, actor="u2", action="B", target="t2")
    assert r1.id is not None and r2.id is not None
    assert r1.id != r2.id
