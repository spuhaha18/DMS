from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import Response
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.deps import get_db, current_user, CurrentUser
from app.models.document import Document
from app.audit.log import log_action
from app.storage.minio_client import get_object
from app.config import settings

router = APIRouter(prefix="/documents", tags=["printing"])


class PrintIn(BaseModel):
    reason: str


@router.post("/{doc_id}/print")
def print_document(
    doc_id: int,
    body: PrintIn,
    request: Request,
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    doc = db.get(Document, doc_id)
    if not doc:
        raise HTTPException(404, "Document not found")
    if doc.effective_status not in ("Effective", "Superseded"):
        raise HTTPException(400, "Document is not in a printable state")
    if not doc.final_pdf_object_key:
        raise HTTPException(400, "No final PDF available")

    ip = request.client.host if request.client else "unknown"
    pdf_bytes = get_object(settings.BUCKET_FINAL, doc.final_pdf_object_key)

    log_action(
        db, actor=user.username, action="PRINT",
        target=f"document:{doc_id}",
        payload={"reason": body.reason, "ip": ip},
    )
    db.commit()

    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={"Content-Disposition": f"inline; filename=doc-{doc_id}.pdf"},
    )
