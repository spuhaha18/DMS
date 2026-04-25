import hashlib
from datetime import datetime, date as date_
from sqlalchemy.orm import Session
from app.models.document import Document
from app.models.approval import Approval, ApprovalTemplate
from app.models.master import DocumentType
from app.audit.log import log_action
from app.pdf.converter import docx_to_pdf
from app.pdf.stamp import append_signature_block
from app.storage.minio_client import get_object, put_object, ensure_buckets
from app.config import settings


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

        # Finalization pipeline: convert DOCX → PDF → stamp → store
        docx_bytes = get_object(settings.BUCKET_SOURCE, doc.source_docx_key)
        pdf_bytes = docx_to_pdf(docx_bytes)

        # Collect approved signatures ordered by step_order then id
        sigs = []
        for ap in (
            db.query(Approval)
            .filter_by(document_id=doc_id)
            .filter(Approval.status == "Approved")
            .order_by(Approval.step_order, Approval.id)
            .all()
        ):
            sigs.append({
                "username": ap.assigned_username,
                "name": ap.assigned_username,  # name not separately stored
                "role": ap.role,
                "ts": str(ap.decided_at),
                "meaning": ap.signature_meaning or "동의",
            })

        pdf_bytes = append_signature_block(pdf_bytes, sigs)
        sha256 = hashlib.sha256(pdf_bytes).hexdigest()
        key = f"final/{doc.doc_number}-rev{doc.revision}.pdf"
        ensure_buckets()
        put_object(settings.BUCKET_FINAL, key, pdf_bytes, "application/pdf")

        doc.final_pdf_object_key = key
        doc.final_pdf_sha256 = sha256
        doc.effective_date = date_.today()

        if doc.parent_document_id and doc.relation_type == "revision":
            parent = db.get(Document, doc.parent_document_id)
            if parent:
                parent.effective_status = "Superseded"

        log_action(db, actor=username, action="DOC_FINALIZED",
                   target=f"document:{doc_id}",
                   payload={"key": key, "sha256": sha256})


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
