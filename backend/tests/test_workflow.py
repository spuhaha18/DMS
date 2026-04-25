from datetime import date, datetime
import pytest
from app.workflow.engine import (
    instantiate_approval_line, review_action, approve_action, withdraw
)


@pytest.fixture
def project(db):
    from app.models.master import Project
    p = Project(code="WF-P", name="Workflow Test", owner_username="alice",
                start_date=date(2026, 1, 1), end_date=date(2027, 12, 31))
    db.add(p)
    db.flush()
    return p


@pytest.fixture
def sop_doctype(db):
    from app.models.master import DocumentType
    dt = DocumentType(
        code="SOP-WF", name="SOP Workflow",
        numbering_pattern="{project_code}-SOP-{seq:04d}",
        allowed_change_types=["New", "Revision"],
        template_version_policy="reject",
    )
    db.add(dt)
    db.flush()
    return dt


@pytest.fixture
def approval_template(db, sop_doctype):
    from app.models.approval import ApprovalTemplate
    at = ApprovalTemplate(
        doc_type_id=sop_doctype.id,
        name="Default SOP Approval",
        config={"reviewers": ["reviewer1", "reviewer2"], "approvers": ["approver1"]},
    )
    db.add(at)
    db.flush()
    # Link to doctype
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
        doc_number="WF-P-SOP-0001", revision="A",
        document_type_id=sop_doctype.id, project_id=project.id,
        author_username="alice", title="Test Doc",
        effective_status="UnderReview",
        template_id=t.id, template_version="1.0",
        source_docx_key="sources/WF-P-SOP-0001.docx",
    )
    db.add(doc)
    db.flush()
    instantiate_approval_line(db, doc, sop_doctype)
    db.flush()
    return doc


def test_serial_review_then_approval(db, submitted_doc):
    doc_id = submitted_doc.id
    # Both reviewers must approve before moving to PendingApproval
    review_action(db, doc_id, "reviewer1", "Approve")
    db.flush()
    db.refresh(submitted_doc)
    assert submitted_doc.effective_status == "UnderReview"  # still waiting for reviewer2

    review_action(db, doc_id, "reviewer2", "Approve")
    db.flush()
    db.refresh(submitted_doc)
    assert submitted_doc.effective_status == "PendingApproval"

    approve_action(db, doc_id, "approver1", "Approve")
    db.flush()
    db.refresh(submitted_doc)
    assert submitted_doc.effective_status == "Effective"


def test_reject_returns_to_draft(db, submitted_doc):
    review_action(db, submitted_doc.id, "reviewer1", "Reject", comment="수정 요망")
    db.flush()
    db.refresh(submitted_doc)
    assert submitted_doc.effective_status == "Draft"


def test_withdraw_by_author(db, submitted_doc):
    withdraw(db, submitted_doc.id, "alice")
    db.flush()
    db.refresh(submitted_doc)
    assert submitted_doc.effective_status == "Draft"
