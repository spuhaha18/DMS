from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from app.deps import get_db, current_user, CurrentUser
from app.models.approval import Approval
from app.models.document import Document

router = APIRouter(prefix="/documents", tags=["documents"])


@router.get("/{doc_id}/approvals")
def approval_history(
    doc_id: int,
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    doc = db.get(Document, doc_id)
    if not doc:
        raise HTTPException(404, "Document not found")
    rows = (
        db.query(Approval)
        .filter_by(document_id=doc_id)
        .order_by(Approval.step_order)
        .all()
    )
    return [
        {
            "step_order": a.step_order,
            "role": a.role,
            "assigned_username": a.assigned_username,
            "status": a.status,
            "comment": a.comment,
            "decided_at": a.decided_at.isoformat() if a.decided_at else None,
        }
        for a in rows
    ]


@router.get("")
def list_documents(
    project_code: str | None = Query(None),
    status: str | None = Query(None),
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    from app.models.master import Project
    q = db.query(Document)
    if project_code:
        pj = db.query(Project).filter_by(code=project_code).first()
        if not pj:
            return []
        q = q.filter(Document.project_id == pj.id)
    if status:
        q = q.filter(Document.effective_status == status)
    docs = q.order_by(Document.id.desc()).all()
    return [
        {
            "document_id": d.id,
            "doc_number": d.doc_number,
            "revision": d.revision,
            "title": d.title,
            "effective_status": d.effective_status,
            "author_username": d.author_username,
        }
        for d in docs
    ]
