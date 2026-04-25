from datetime import date
from unittest.mock import patch, MagicMock
import pytest
from app.workflow.engine import (
    instantiate_approval_line, review_action, approve_action
)


@pytest.fixture
def project(db):
    from app.models.master import Project
    p = Project(code="FIN-P", name="Finalize Test", owner_username="alice",
                start_date=date(2026, 1, 1), end_date=date(2027, 12, 31))
    db.add(p)
    db.flush()
    return p


@pytest.fixture
def sop_doctype(db):
    from app.models.master import DocumentType
    dt = DocumentType(code="SOP-FIN", name="SOP Finalize",
                      numbering_pattern="{project_code}-SOP-{seq:04d}",
                      allowed_change_types=["New"],
                      template_version_policy="reject")
    db.add(dt)
    db.flush()
    return dt


@pytest.fixture
def approval_template(db, sop_doctype):
    from app.models.approval import ApprovalTemplate
    at = ApprovalTemplate(
        doc_type_id=sop_doctype.id,
        name="Default",
        config={"reviewers": ["reviewer1"], "approvers": ["approver1"]},
    )
    db.add(at)
    db.flush()
    sop_doctype.default_approval_template_id = at.id
    db.flush()
    return at


@pytest.fixture
def submitted_doc(db, project, sop_doctype, approval_template):
    from app.models.master import Template
    from app.models.document import Document
    t = Template(doc_type_id=sop_doctype.id, version="1.0",
                 effective_date=date(2026, 1, 1),
                 object_key="templates/t.docx", sha256="abc")
    db.add(t)
    db.flush()
    doc = Document(
        doc_number="FIN-P-SOP-0001", revision="A",
        document_type_id=sop_doctype.id, project_id=project.id,
        author_username="alice", title="Finalize Test Doc",
        effective_status="UnderReview",
        template_id=t.id, template_version="1.0",
        source_docx_key="sources/FIN-P-SOP-0001.docx",
    )
    db.add(doc)
    db.flush()
    instantiate_approval_line(db, doc, sop_doctype)
    db.flush()
    return doc


@patch("app.workflow.engine.put_object")
@patch("app.workflow.engine.get_object")
@patch("app.workflow.engine.ensure_buckets")
@patch("app.workflow.engine.docx_to_pdf")
def test_final_approval_finalizes_document(mock_convert, mock_ensure, mock_get, mock_put, db, submitted_doc):
    import io
    from reportlab.pdfgen import canvas
    from reportlab.lib.pagesizes import A4

    # Produce a real minimal PDF for stamp test
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    c.drawString(100, 700, "Test")
    c.save()
    mock_convert.return_value = buf.getvalue()
    mock_get.return_value = b"fake docx"

    doc_id = submitted_doc.id

    review_action(db, doc_id, "reviewer1", "Approve")
    db.flush()

    approve_action(db, doc_id, "approver1", "Approve")
    db.flush()
    db.refresh(submitted_doc)

    assert submitted_doc.effective_status == "Effective"
    assert submitted_doc.final_pdf_object_key is not None
    assert submitted_doc.final_pdf_sha256 is not None
    mock_put.assert_called_once()
