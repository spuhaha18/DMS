from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from typing import List
from sqlalchemy.orm import Session
from app.deps import get_db, require_role, current_user, CurrentUser
from app.models.master import DocumentType
from app.audit.log import log_action

router = APIRouter(prefix="/master/document-types", tags=["master"])


class DocumentTypeIn(BaseModel):
    code: str
    name: str
    numbering_pattern: str
    allowed_change_types: List[str]
    default_validity_months: int | None = None
    template_version_policy: str = "reject"


@router.post("", status_code=201)
async def create(body: DocumentTypeIn, user: CurrentUser = Depends(require_role("Admin")), db: Session = Depends(get_db)):
    if db.query(DocumentType).filter_by(code=body.code).first():
        raise HTTPException(409, "Duplicate document type code")
    dt = DocumentType(
        code=body.code, name=body.name,
        numbering_pattern=body.numbering_pattern,
        allowed_change_types=body.allowed_change_types,
        default_validity_months=body.default_validity_months,
        template_version_policy=body.template_version_policy,
    )
    db.add(dt); db.flush()
    log_action(db, actor=user.username, action="MASTER_DOCTYPE_CREATE",
               target=f"doctype:{dt.id}", payload={"code": body.code})
    db.commit()
    return {"id": dt.id, "code": dt.code}


@router.get("")
async def list_doctypes(db: Session = Depends(get_db), user: CurrentUser = Depends(current_user)):
    return [{"id": d.id, "code": d.code, "name": d.name} for d in db.query(DocumentType).all()]
