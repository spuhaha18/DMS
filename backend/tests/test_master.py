import pytest
from unittest.mock import patch, MagicMock


def make_admin_token(test_client):
    with patch("app.auth.ldap.Server") as ms, patch("app.auth.ldap.Connection") as mc:
        ms.return_value = MagicMock()
        conn = MagicMock()
        mc.return_value = conn
        conn.entries = [MagicMock(
            sAMAccountName="admin", mail="admin@x", department="IT",
            title="Admin", memberOf=["CN=DMS-Admin,OU=Groups,DC=x,DC=com"]
        )]
        r = test_client.post("/auth/login", json={"username": "admin", "password": "pw"})
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def make_author_token(test_client):
    with patch("app.auth.ldap.Server") as ms, patch("app.auth.ldap.Connection") as mc:
        ms.return_value = MagicMock()
        conn = MagicMock()
        mc.return_value = conn
        conn.entries = [MagicMock(
            sAMAccountName="author", mail="author@x", department="R&D",
            title="연구원", memberOf=[]
        )]
        r = test_client.post("/auth/login", json={"username": "author", "password": "pw"})
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def test_create_project(test_client):
    admin = make_admin_token(test_client)
    r = test_client.post("/master/projects",
                         json={"code": "P-001", "name": "파일럿", "owner": "alice",
                               "start": "2026-01-01", "end": "2027-12-31"},
                         headers=admin)
    assert r.status_code == 201
    assert r.json()["code"] == "P-001"


def test_project_code_unique(test_client):
    admin = make_admin_token(test_client)
    test_client.post("/master/projects",
                     json={"code": "P-DUP", "name": "a", "owner": "alice",
                           "start": "2026-01-01", "end": "2026-12-31"},
                     headers=admin)
    r = test_client.post("/master/projects",
                         json={"code": "P-DUP", "name": "b", "owner": "alice",
                               "start": "2026-01-01", "end": "2026-12-31"},
                         headers=admin)
    assert r.status_code == 409


def test_create_document_type(test_client):
    admin = make_admin_token(test_client)
    r = test_client.post("/master/document-types",
                         json={"code": "SOP", "name": "SOP",
                               "numbering_pattern": "{project_code}-SOP-{seq:04d}",
                               "allowed_change_types": ["Revision", "Errata", "Addendum", "New"],
                               "default_validity_months": 36,
                               "template_version_policy": "reject"},
                         headers=admin)
    assert r.status_code == 201


def test_create_organization(test_client):
    admin = make_admin_token(test_client)
    r = test_client.post("/master/organizations",
                         json={"code": "R&D", "name": "연구개발팀"},
                         headers=admin)
    assert r.status_code == 201
    assert r.json()["code"] == "R&D"


def test_create_user(test_client):
    admin = make_admin_token(test_client)
    r = test_client.post("/master/users",
                         json={"username": "bob", "email": "bob@x.com", "role": "Reviewer"},
                         headers=admin)
    assert r.status_code == 201
    assert r.json()["username"] == "bob"


def test_list_projects(test_client):
    admin = make_admin_token(test_client)
    test_client.post("/master/projects",
                     json={"code": "P-LIST", "name": "List Test", "owner": "alice",
                           "start": "2026-01-01", "end": "2027-12-31"},
                     headers=admin)
    r = test_client.get("/master/projects", headers=admin)
    assert r.status_code == 200
    assert isinstance(r.json(), list)


def test_project_post_requires_admin(test_client):
    author = make_author_token(test_client)
    r = test_client.post("/master/projects",
                         json={"code": "P-FORBIDDEN", "name": "x", "owner": "alice",
                               "start": "2026-01-01", "end": "2027-12-31"},
                         headers=author)
    assert r.status_code == 403


def test_project_post_requires_auth(test_client):
    r = test_client.post("/master/projects",
                         json={"code": "P-NOAUTH", "name": "x", "owner": "alice",
                               "start": "2026-01-01", "end": "2027-12-31"})
    assert r.status_code == 401
