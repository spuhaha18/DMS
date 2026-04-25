from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.deps import get_db, require_role, current_user, CurrentUser
from app.models.master import Organization
from app.audit.log import log_action

router = APIRouter(prefix="/master/organizations", tags=["master"])


class OrgIn(BaseModel):
    code: str
    name: str
    parent_id: int | None = None


@router.post("", status_code=201)
async def create(body: OrgIn, user: CurrentUser = Depends(require_role("Admin")), db: Session = Depends(get_db)):
    if db.query(Organization).filter_by(code=body.code).first():
        raise HTTPException(409, "Duplicate org code")
    o = Organization(code=body.code, name=body.name, parent_id=body.parent_id)
    db.add(o); db.flush()
    log_action(db, actor=user.username, action="MASTER_ORG_CREATE",
               target=f"org:{o.id}", payload={"code": body.code})
    db.commit()
    return {"id": o.id, "code": o.code}


@router.get("")
async def list_orgs(db: Session = Depends(get_db), user: CurrentUser = Depends(current_user)):
    return [{"id": o.id, "code": o.code, "name": o.name} for o in db.query(Organization).all()]
