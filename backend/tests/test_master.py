import uuid
import pytest
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


@pytest.fixture
def admin_token():
    with patch("app.auth.ldap.Server") as ms, patch("app.auth.ldap.Connection") as mc:
        ms.return_value = MagicMock()
        conn = MagicMock()
        mc.return_value = conn
        conn.entries = [MagicMock(
            sAMAccountName="admin", mail="admin@x", department="IT",
            title="Admin", memberOf=["CN=DMS-Admin,OU=Groups,DC=x,DC=com"]
        )]
        r = client.post("/auth/login", json={"username": "admin", "password": "pw"})
    token = r.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def unique(prefix: str) -> str:
    """Generate a unique code to avoid cross-run collisions in the shared DB."""
    return f"{prefix}-{uuid.uuid4().hex[:6].upper()}"


def test_create_project(admin_token):
    code = unique("P")
    r = client.post("/master/projects",
                    json={"code": code, "name": "파일럿", "owner": "alice",
                          "start": "2026-01-01", "end": "2027-12-31"},
                    headers=admin_token)
    assert r.status_code == 201
    assert r.json()["code"] == code


def test_project_code_unique(admin_token):
    code = unique("DUP")
    client.post("/master/projects",
                json={"code": code, "name": "a", "owner": "alice",
                      "start": "2026-01-01", "end": "2026-12-31"},
                headers=admin_token)
    r = client.post("/master/projects",
                    json={"code": code, "name": "b", "owner": "alice",
                          "start": "2026-01-01", "end": "2026-12-31"},
                    headers=admin_token)
    assert r.status_code == 409


def test_create_document_type(admin_token):
    code = unique("SOP")
    r = client.post("/master/document-types",
                    json={"code": code, "name": "SOP",
                          "numbering_pattern": "{project_code}-SOP-{seq:04d}",
                          "allowed_change_types": ["Revision", "Errata", "Addendum", "New"],
                          "default_validity_months": 36,
                          "template_version_policy": "reject"},
                    headers=admin_token)
    assert r.status_code == 201
