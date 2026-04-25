from datetime import date
from unittest.mock import patch
import pytest


@pytest.fixture
def effective_doc(test_client):
    from app.models.master import Project, DocumentType, Template
    from app.models.document import Document

    session = test_client._test_session

    p = Project(code="PRINT-P", name="Print Test", owner_username="alice",
                start_date=date(2026, 1, 1), end_date=date(2027, 12, 31))
    session.add(p)
    session.flush()

    dt = DocumentType(code="SOP-PR", name="SOP Print",
                      numbering_pattern="{project_code}-SOP-{seq:04d}",
                      allowed_change_types=["New"],
                      template_version_policy="reject")
    session.add(dt)
    session.flush()

    t = Template(doc_type_id=dt.id, version="1.0",
                 effective_date=date(2026, 1, 1),
                 object_key="templates/t.docx", sha256="abc")
    session.add(t)
    session.flush()

    doc = Document(
        doc_number="PRINT-P-SOP-0001", revision="A",
        document_type_id=dt.id, project_id=p.id,
        author_username="alice", title="Print Test Doc",
        effective_status="Effective",
        template_id=t.id, template_version="1.0",
        source_docx_key="sources/PRINT-P-SOP-0001.docx",
        final_pdf_object_key="final/PRINT-P-SOP-0001-revA.pdf",
        final_pdf_sha256="fakeshahex",
    )
    session.add(doc)
    session.flush()
    return doc


def _auth_header(username="alice", role="Author"):
    from app.auth.session import issue
    token = issue(username, role)
    return {"Authorization": f"Bearer {token}"}


@patch("app.printing.api.get_object", return_value=b"%PDF-1.4 fake pdf content")
def test_print_logs_audit_and_returns_pdf(mock_get, test_client, effective_doc):
    headers = _auth_header()
    r = test_client.post(
        f"/documents/{effective_doc.id}/print",
        json={"reason": "교육"},
        headers=headers,
    )
    assert r.status_code == 200
    assert r.content[:4] == b"%PDF"
    assert r.headers["content-type"] == "application/pdf"
    mock_get.assert_called_once()


@patch("app.printing.api.get_object", return_value=b"%PDF-1.4 fake pdf content")
def test_print_draft_document_rejected(mock_get, test_client):
    from app.models.master import Project, DocumentType, Template
    from app.models.document import Document

    session = test_client._test_session

    p = Project(code="PRINT-D", name="Draft Print", owner_username="alice",
                start_date=date(2026, 1, 1), end_date=date(2027, 12, 31))
    session.add(p)
    session.flush()

    dt = DocumentType(code="SOP-PD", name="SOP Draft",
                      numbering_pattern="{project_code}-SOP-{seq:04d}",
                      allowed_change_types=["New"],
                      template_version_policy="reject")
    session.add(dt)
    session.flush()

    t = Template(doc_type_id=dt.id, version="1.0",
                 effective_date=date(2026, 1, 1),
                 object_key="templates/td.docx", sha256="abc")
    session.add(t)
    session.flush()

    doc = Document(
        doc_number="PRINT-D-SOP-0001", revision="A",
        document_type_id=dt.id, project_id=p.id,
        author_username="alice", title="Draft Doc",
        effective_status="Draft",
        template_id=t.id, template_version="1.0",
        source_docx_key="sources/draft.docx",
    )
    session.add(doc)
    session.flush()

    headers = _auth_header()
    r = test_client.post(
        f"/documents/{doc.id}/print",
        json={"reason": "test"},
        headers=headers,
    )
    assert r.status_code == 400
