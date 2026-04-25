import io
import zipfile
from datetime import date
from unittest.mock import patch, MagicMock
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def project(test_client):
    from app.models.master import Project
    session = test_client._test_session
    p = Project(code="SUBMIT-P", name="Submit Test", owner_username="alice",
                start_date=date(2026, 1, 1), end_date=date(2027, 12, 31))
    session.add(p)
    session.flush()
    return p


@pytest.fixture
def sop_doctype(test_client):
    from app.models.master import DocumentType
    session = test_client._test_session
    dt = DocumentType(code="SOP-S", name="SOP Submit",
                      numbering_pattern="{project_code}-SOP-{seq:04d}",
                      allowed_change_types=["New", "Revision"],
                      template_version_policy="reject")
    session.add(dt)
    session.flush()
    return dt


@pytest.fixture
def sop_template(test_client, sop_doctype):
    from app.models.master import Template
    session = test_client._test_session
    t = Template(doc_type_id=sop_doctype.id, version="1.0",
                 effective_date=date(2026, 1, 1),
                 object_key="templates/test.docx", sha256="abc123")
    session.add(t)
    session.flush()
    return t


def _make_docx_with_props(template_id: int, template_version: str) -> bytes:
    """Build a minimal docx ZIP with custom.xml containing templateId and templateVersion."""
    buf = io.BytesIO()
    CP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
    VT_NS = "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"
    from lxml import etree
    root = etree.Element(f"{{{CP_NS}}}Properties", nsmap={None: CP_NS, "vt": VT_NS})
    for i, (k, v) in enumerate([("templateId", str(template_id)), ("templateVersion", template_version)], start=2):
        p = etree.SubElement(root, f"{{{CP_NS}}}property",
                             fmtid="{D5CDD505-2E9C-101B-9397-08002B2CF9AE}", pid=str(i), name=k)
        etree.SubElement(p, f"{{{VT_NS}}}lpwstr").text = v
    custom_xml = b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' + etree.tostring(root)
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("word/document.xml", b"<w:document/>")
        z.writestr("docProps/custom.xml", custom_xml)
        z.writestr("[Content_Types].xml", b'<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"></Types>')
        z.writestr("_rels/.rels", b'<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>')
    return buf.getvalue()


def _auth_header(test_client, username="alice", role="Author"):
    from app.auth.session import issue
    token = issue(username, role)
    return {"Authorization": f"Bearer {token}"}


@patch("app.documents.submit_api.put_object")
@patch("app.documents.submit_api.ensure_buckets")
def test_submit_new_document(mock_ensure, mock_put, test_client, project, sop_doctype, sop_template):
    docx = _make_docx_with_props(sop_template.id, "1.0")
    headers = _auth_header(test_client)
    r = test_client.post(
        "/documents/submit",
        files={"file": ("draft.docx", docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
        data={"project_code": "SUBMIT-P", "doc_type_code": "SOP-S",
              "change_type": "New", "title": "Test SOP"},
        headers=headers,
    )
    assert r.status_code == 201, r.json()
    body = r.json()
    assert body["doc_number"].endswith("-SOP-0001")
    assert body["revision"] == "A"


@patch("app.documents.submit_api.put_object")
@patch("app.documents.submit_api.ensure_buckets")
def test_submit_rejects_wrong_template_version(mock_ensure, mock_put, test_client, project, sop_doctype, sop_template):
    # sop_template has version "1.0" but we send "0.9" — policy=reject → 400
    docx = _make_docx_with_props(sop_template.id, "0.9")
    headers = _auth_header(test_client)
    r = test_client.post(
        "/documents/submit",
        files={"file": ("draft.docx", docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
        data={"project_code": "SUBMIT-P", "doc_type_code": "SOP-S",
              "change_type": "New", "title": "Test SOP"},
        headers=headers,
    )
    assert r.status_code == 400
    assert "template" in r.json()["detail"].lower() or "버전" in r.json()["detail"]


@patch("app.documents.submit_api.put_object")
@patch("app.documents.submit_api.ensure_buckets")
def test_submit_rejects_disallowed_change_type(mock_ensure, mock_put, test_client, project, sop_doctype, sop_template):
    # sop_doctype only allows ["New", "Revision"] — "Amendment" should be rejected
    docx = _make_docx_with_props(sop_template.id, "1.0")
    headers = _auth_header(test_client)
    r = test_client.post(
        "/documents/submit",
        files={"file": ("draft.docx", docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
        data={"project_code": "SUBMIT-P", "doc_type_code": "SOP-S",
              "change_type": "Amendment", "title": "Test SOP"},
        headers=headers,
    )
    assert r.status_code == 400
    assert "change_type" in r.json()["detail"].lower() or "not allowed" in r.json()["detail"].lower()
