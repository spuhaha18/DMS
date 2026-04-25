import pytest
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient
from app.main import app


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def mock_ldap():
    with patch("app.auth.ldap.Server") as mock_server, \
         patch("app.auth.ldap.Connection") as mock_conn:
        mock_server.return_value = MagicMock()
        conn = MagicMock()
        mock_conn.return_value = conn
        conn.entries = [MagicMock(
            sAMAccountName="alice", mail="alice@example.com",
            department="R&D", title="연구원", memberOf=[]
        )]
        yield conn


def test_login_success(client, mock_ldap):
    r = client.post("/auth/login", json={"username": "alice", "password": "pw"})
    assert r.status_code == 200
    assert "access_token" in r.json()


def test_login_bad_credentials(client):
    with patch("app.auth.ldap.Server"), \
         patch("app.auth.ldap.Connection", side_effect=Exception("invalid")):
        r = client.post("/auth/login", json={"username": "alice", "password": "wrong"})
    assert r.status_code == 401


def test_me_requires_token(client):
    r = client.get("/auth/me")
    assert r.status_code == 401


def test_me_with_valid_token(client, mock_ldap):
    login_r = client.post("/auth/login", json={"username": "alice", "password": "pw"})
    token = login_r.json()["access_token"]
    r = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 200
    assert r.json()["username"] == "alice"
