from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from app.deps import get_db, current_user, CurrentUser
from app.models.document import Document

router = APIRouter(prefix="/documents", tags=["documents"])


@router.get("/by-number")
def get_by_doc_number(
    doc_number: str = Query(...),
    latest_effective: bool = Query(False),
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    """Look up document(s) by doc_number. With latest_effective=true, returns the Effective revision."""
    q = db.query(Document).filter(Document.doc_number == doc_number)
    if latest_effective:
        q = q.filter(Document.effective_status == "Effective")
    d = q.order_by(Document.revision.desc()).first()
    if not d:
        raise HTTPException(404, "Document not found")
    return {
        "id": d.id,
        "doc_number": d.doc_number,
        "revision": d.revision,
        "title": d.title,
        "effective_status": d.effective_status,
        "effective_date": str(d.effective_date) if d.effective_date else None,
    }


@router.get("/{doc_id}/lineage")
def get_lineage(
    doc_id: int,
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    """Return the full lineage chain: ancestors + target + direct children."""
    target = db.get(Document, doc_id)
    if not target:
        raise HTTPException(404, "Document not found")

    # Walk up to root
    ancestors = []
    cur = target
    while cur.parent_document_id:
        cur = db.get(Document, cur.parent_document_id)
        ancestors.insert(0, cur)

    # Direct children of target
    children = (
        db.query(Document)
        .filter(Document.parent_document_id == target.id)
        .order_by(Document.id)
        .all()
    )

    chain = ancestors + [target] + children

    return {
        "chain": [
            {
                "id": d.id,
                "doc_number": d.doc_number,
                "revision": d.revision,
                "title": d.title,
                "effective_status": d.effective_status,
                "effective_date": str(d.effective_date) if d.effective_date else None,
                "relation_type": d.relation_type,
            }
            for d in chain
        ]
    }
