from datetime import datetime
from sqlalchemy.orm import Session
from app.models.document import Document
from app.models.approval import Approval, ApprovalTemplate
from app.models.master import DocumentType
from app.audit.log import log_action


def instantiate_approval_line(db: Session, doc: Document, dt: DocumentType) -> None:
    """Create approval rows for a document based on its doc type's approval template."""
    if not dt.default_approval_template_id:
        return  # No approval template configured — skip
    at = db.get(ApprovalTemplate, dt.default_approval_template_id)
    if not at:
        return

    # Step 1: Author (auto-approved)
    db.add(Approval(
        document_id=doc.id, step_order=1, role="Author",
        assigned_username=doc.author_username, status="Approved",
        decided_at=datetime.utcnow()
    ))
    # Step 2: Reviewers (parallel, all must approve)
    for username in at.config.get("reviewers", []):
        db.add(Approval(
            document_id=doc.id, step_order=2, role="Reviewer",
            assigned_username=username, status="Pending"
        ))
    # Step 3: Approvers
    for username in at.config.get("approvers", []):
        db.add(Approval(
            document_id=doc.id, step_order=3, role="Approver",
            assigned_username=username, status="Pending"
        ))
    db.flush()


def _all_step_done(db: Session, doc_id: int, step: int) -> bool:
    rows = db.query(Approval).filter_by(document_id=doc_id, step_order=step).all()
    return bool(rows) and all(r.status in ("Approved", "Delegated") for r in rows)


def review_action(db: Session, doc_id: int, username: str, decision: str, *, comment: str | None = None) -> None:
    """Reviewer approves or rejects. If all reviewers done → PendingApproval. If any rejects → Draft."""
    a = (
        db.query(Approval)
        .filter_by(document_id=doc_id, step_order=2, assigned_username=username, status="Pending")
        .one()
    )
    a.status = "Approved" if decision == "Approve" else "Rejected"
    a.decided_at = datetime.utcnow()
    a.comment = comment
    a.signature_meaning = "검토 동의" if decision == "Approve" else None
    db.flush()

    doc = db.get(Document, doc_id)
    if a.status == "Rejected":
        doc.effective_status = "Draft"
        log_action(db, actor=username, action="REVIEW_REJECTED",
                   target=f"document:{doc_id}", payload={"comment": comment})
    elif _all_step_done(db, doc_id, 2):
        doc.effective_status = "PendingApproval"
        log_action(db, actor=username, action="REVIEW_APPROVED",
                   target=f"document:{doc_id}", payload={})


def approve_action(db: Session, doc_id: int, username: str, decision: str, *, comment: str | None = None) -> None:
    """Final approver approves or rejects."""
    a = (
        db.query(Approval)
        .filter_by(document_id=doc_id, step_order=3, assigned_username=username, status="Pending")
        .one()
    )
    a.status = "Approved" if decision == "Approve" else "Rejected"
    a.decided_at = datetime.utcnow()
    a.comment = comment
    a.signature_meaning = "승인" if decision == "Approve" else None
    db.flush()

    doc = db.get(Document, doc_id)
    if a.status == "Rejected":
        doc.effective_status = "Draft"
        log_action(db, actor=username, action="APPROVE_REJECTED",
                   target=f"document:{doc_id}", payload={"comment": comment})
    else:
        doc.effective_status = "Effective"
        log_action(db, actor=username, action="APPROVE_APPROVED",
                   target=f"document:{doc_id}", payload={})


def withdraw(db: Session, doc_id: int, username: str) -> None:
    """Author withdraws a document, resetting to Draft and marking pending approvals as Withdrawn."""
    doc = db.get(Document, doc_id)
    if doc.author_username != username:
        raise ValueError("Only the document author can withdraw")
    pending = db.query(Approval).filter_by(document_id=doc_id, status="Pending").all()
    for a in pending:
        a.status = "Withdrawn"
        a.decided_at = datetime.utcnow()
    doc.effective_status = "Draft"
    log_action(db, actor=username, action="DOC_WITHDRAWN",
               target=f"document:{doc_id}", payload={})
    db.flush()
