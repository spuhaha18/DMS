from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from app.deps import get_db, current_user, CurrentUser
from app.models.document import Document

router = APIRouter(prefix="/documents", tags=["documents"])


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
        if pj:
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
