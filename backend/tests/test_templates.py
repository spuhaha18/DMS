import io
import zipfile
import pytest
from unittest.mock import patch, MagicMock
from lxml import etree

# Helper: build a minimal valid docx bytes
def make_docx() -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '</Types>')
        z.writestr("_rels/.rels",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '</Relationships>')
        z.writestr("word/document.xml",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            '<w:body><w:p><w:r><w:t>Test</w:t></w:r></w:p></w:body></w:document>')
    return buf.getvalue()


@pytest.fixture
def admin_token(test_client):
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


def test_inject_custom_properties():
    from app.master.templates_api import inject_custom_properties, read_custom_properties
    docx = make_docx()
    injected = inject_custom_properties(docx, {"templateId": "42", "templateVersion": "1.0"})
    props = read_custom_properties(injected)
    assert props["templateId"] == "42"
    assert props["templateVersion"] == "1.0"


def test_upload_template(test_client, admin_token):
    # First create a document type
    test_client.post("/master/document-types",
                     json={"code": "SOP-T", "name": "SOP", "numbering_pattern": "{seq:04d}",
                           "allowed_change_types": ["New"], "template_version_policy": "reject"},
                     headers=admin_token)

    docx_bytes = make_docx()
    with patch("app.master.templates_api.ensure_buckets"), \
         patch("app.master.templates_api.put_object"), \
         patch("app.master.templates_api.get_object", return_value=docx_bytes):
        r = test_client.post(
            "/master/templates",
            files={"file": ("sop.docx", io.BytesIO(docx_bytes),
                           "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
            data={"doc_type_code": "SOP-T", "version": "1.0", "effective_date": "2026-01-01"},
            headers=admin_token,
        )
    assert r.status_code == 201
    assert "template_id" in r.json()


def test_upload_template_wrong_doctype(test_client, admin_token):
    docx_bytes = make_docx()
    with patch("app.master.templates_api.ensure_buckets"), \
         patch("app.master.templates_api.put_object"):
        r = test_client.post(
            "/master/templates",
            files={"file": ("sop.docx", io.BytesIO(docx_bytes),
                           "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
            data={"doc_type_code": "NONEXISTENT", "version": "1.0", "effective_date": "2026-01-01"},
            headers=admin_token,
        )
    assert r.status_code == 404
