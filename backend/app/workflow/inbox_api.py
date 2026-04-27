from typing import Literal
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.deps import get_db, current_user, CurrentUser
from app.models.approval import Approval
from app.models.document import Document
from app.workflow.engine import review_action, approve_action, withdraw

router = APIRouter(prefix="/workflow", tags=["workflow"])


class DecideIn(BaseModel):
    decision: Literal["Approve", "Reject"]
    comment: str | None = None


@router.get("/inbox")
def inbox(user: CurrentUser = Depends(current_user), db: Session = Depends(get_db)):
    rows = (
        db.query(Approval, Document)
        .join(Document, Document.id == Approval.document_id)
        .filter(Approval.assigned_username == user.username, Approval.status == "Pending")
        .all()
    )
    return [
        {
            "approval_id": a.id,
            "document_id": d.id,
            "doc_number": d.doc_number,
            "title": d.title,
            "role": a.role,
            "submitted_by": d.author_username,
        }
        for a, d in rows
    ]


@router.post("/inbox/{approval_id}/decide")
def decide(
    approval_id: int,
    body: DecideIn,
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    a = db.get(Approval, approval_id)
    if not a:
        raise HTTPException(404, "Approval not found")
    if a.assigned_username != user.username:
        raise HTTPException(403, "Not your approval")
    if a.status != "Pending":
        raise HTTPException(400, "Approval already decided")

    if a.role == "Reviewer":
        review_action(db, a.document_id, user.username, body.decision, comment=body.comment)
    elif a.role == "Approver":
        doc = db.get(Document, a.document_id)
        if not doc or doc.effective_status != "PendingApproval":
            raise HTTPException(400, "Document is not ready for final approval")
        approve_action(db, a.document_id, user.username, body.decision, comment=body.comment)
    else:
        raise HTTPException(400, f"Cannot decide on role {a.role}")

    db.commit()
    return {"ok": True}


@router.post("/documents/{doc_id}/withdraw")
def withdraw_document(
    doc_id: int,
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    doc = db.get(Document, doc_id)
    if not doc:
        raise HTTPException(404, "Document not found")
    try:
        withdraw(db, doc_id, user.username)
    except ValueError as e:
        raise HTTPException(403, str(e))
    db.commit()
    return {"ok": True}
