"""E2E pilot scenario: full document lifecycle from submit to print."""
import io
import zipfile
from datetime import date
from unittest.mock import patch
import pytest
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4

pytestmark = pytest.mark.e2e


def _make_docx(template_id: int, template_version: str) -> bytes:
    """Minimal valid docx with custom properties."""
    from lxml import etree
    CP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
    VT_NS = "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"
    root = etree.Element(f"{{{CP_NS}}}Properties", nsmap={None: CP_NS, "vt": VT_NS})
    for i, (k, v) in enumerate(
        [("templateId", str(template_id)), ("templateVersion", template_version)], start=2
    ):
        p = etree.SubElement(
            root, f"{{{CP_NS}}}property",
            fmtid="{D5CDD505-2E9C-101B-9397-08002B2CF9AE}", pid=str(i), name=k,
        )
        etree.SubElement(p, f"{{{VT_NS}}}lpwstr").text = v
    custom_xml = b'<?xml version="1.0" encoding="UTF-8"?>\n' + etree.tostring(root)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("word/document.xml", b"<w:document/>")
        z.writestr("docProps/custom.xml", custom_xml)
        z.writestr(
            "[Content_Types].xml",
            b'<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"></Types>',
        )
        z.writestr(
            "_rels/.rels",
            b'<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>',
        )
    return buf.getvalue()


def _token(username: str, role: str) -> dict:
    from app.auth.session import issue
    return {"Authorization": f"Bearer {issue(username, role)}"}


def _minimal_pdf() -> bytes:
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    c.drawString(100, 700, "Test Document")
    c.save()
    return buf.getvalue()


@pytest.fixture
def pilot_setup(test_client):
    """Set up pilot prerequisites using the shared test_client session."""
    from app.models.master import Project, DocumentType, Template
    from app.models.approval import ApprovalTemplate

    session = test_client._test_session

    p = Project(
        code="E2E-P", name="E2E Pilot", owner_username="author1",
        start_date=date(2026, 1, 1), end_date=date(2027, 12, 31),
    )
    session.add(p)
    session.flush()

    dt = DocumentType(
        code="SOP-E2E", name="SOP E2E",
        numbering_pattern="{project_code}-SOP-{seq:04d}",
        allowed_change_types=["New"],
        template_version_policy="reject",
    )
    session.add(dt)
    session.flush()

    at = ApprovalTemplate(
        doc_type_id=dt.id,
        name="E2E Approval",
        config={"reviewers": ["reviewer1"], "approvers": ["approver1"]},
    )
    session.add(at)
    session.flush()
    dt.default_approval_template_id = at.id
    session.flush()

    t = Template(
        doc_type_id=dt.id, version="1.0",
        effective_date=date(2026, 1, 1),
        object_key="templates/e2e.docx", sha256="e2e_sha",
    )
    session.add(t)
    session.flush()

    return {"project": p, "doctype": dt, "approval_template": at, "template": t}


@patch("app.workflow.engine.put_object")
@patch("app.workflow.engine.get_object")
@patch("app.workflow.engine.ensure_buckets")
@patch("app.workflow.engine.docx_to_pdf")
@patch("app.documents.submit_api.put_object")
@patch("app.documents.submit_api.ensure_buckets")
@patch("app.printing.api.get_object")
def test_full_pilot_lifecycle(
    mock_print_get,
    mock_submit_ensure,
    mock_submit_put,
    mock_engine_convert,
    mock_engine_ensure,
    mock_engine_get,
    mock_engine_put,
    test_client,
    pilot_setup,
):
    """Full lifecycle: submit → review → approve → Effective → print."""
    mock_engine_convert.return_value = _minimal_pdf()
    mock_engine_get.return_value = b"fake docx bytes"
    mock_print_get.return_value = b"%PDF-1.4 final pdf content"

    tpl = pilot_setup["template"]
    docx = _make_docx(tpl.id, "1.0")

    # 1. Submit document
    r = test_client.post(
        "/documents/submit",
        files={"file": (
            "draft.docx", docx,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )},
        data={
            "project_code": "E2E-P",
            "doc_type_code": "SOP-E2E",
            "change_type": "New",
            "title": "E2E 파일럿 SOP",
        },
        headers=_token("author1", "Author"),
    )
    assert r.status_code == 201, f"submit failed: {r.json()}"
    doc_id = r.json()["document_id"]
    doc_number = r.json()["doc_number"]
    assert doc_number.endswith("-SOP-0001")

    # 2. Verify document is UnderReview
    docs = test_client.get("/documents", headers=_token("author1", "Author")).json()
    submitted = next((d for d in docs if d["document_id"] == doc_id), None)
    assert submitted is not None
    assert submitted["effective_status"] == "UnderReview"

    # 3. Reviewer approves via inbox
    inbox = test_client.get("/workflow/inbox", headers=_token("reviewer1", "Reviewer")).json()
    reviewer_approval = next((i for i in inbox if i["document_id"] == doc_id), None)
    assert reviewer_approval is not None, f"Reviewer inbox: {inbox}"

    r = test_client.post(
        f"/workflow/inbox/{reviewer_approval['approval_id']}/decide",
        json={"decision": "Approve", "comment": "검토 완료"},
        headers=_token("reviewer1", "Reviewer"),
    )
    assert r.status_code == 200, f"reviewer decide: {r.json()}"

    # 4. Approver approves
    inbox = test_client.get("/workflow/inbox", headers=_token("approver1", "Approver")).json()
    approver_approval = next((i for i in inbox if i["document_id"] == doc_id), None)
    assert approver_approval is not None, f"Approver inbox: {inbox}"

    r = test_client.post(
        f"/workflow/inbox/{approver_approval['approval_id']}/decide",
        json={"decision": "Approve", "comment": "최종 승인"},
        headers=_token("approver1", "Approver"),
    )
    assert r.status_code == 200, f"approver decide: {r.json()}"

    # 5. Verify Effective status and PDF
    docs = test_client.get("/documents", headers=_token("author1", "Author")).json()
    effective_doc = next((d for d in docs if d["document_id"] == doc_id), None)
    assert effective_doc is not None
    assert effective_doc["effective_status"] == "Effective"

    # Verify MinIO put was called for final PDF
    mock_engine_put.assert_called_once()

    # 6. Print (audit)
    r = test_client.post(
        f"/documents/{doc_id}/print",
        json={"reason": "E2E 교육용"},
        headers=_token("author1", "Author"),
    )
    assert r.status_code == 200, f"print failed: {r.text}"
    assert r.content[:4] == b"%PDF"

    # 7. Verify PRINT audit log
    from app.models.audit_log import AuditLog
    session = test_client._test_session
    print_logs = session.query(AuditLog).filter_by(action="PRINT").all()
    assert len(print_logs) >= 1
    assert any(f"document:{doc_id}" == log.target for log in print_logs)
