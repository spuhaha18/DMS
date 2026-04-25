from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from datetime import date
from sqlalchemy.orm import Session
from app.deps import get_db, require_role, current_user, CurrentUser
from app.models.master import Project
from app.audit.log import log_action

router = APIRouter(prefix="/master/projects", tags=["master"])


class ProjectIn(BaseModel):
    code: str
    name: str
    owner: str
    start: date
    end: date


class ProjectOut(BaseModel):
    id: int
    code: str


@router.post("", status_code=201, response_model=ProjectOut)
async def create(body: ProjectIn, user: CurrentUser = Depends(require_role("Admin")), db: Session = Depends(get_db)):
    if db.query(Project).filter_by(code=body.code).first():
        raise HTTPException(409, "Duplicate project code")
    p = Project(code=body.code, name=body.name, owner_username=body.owner,
                start_date=body.start, end_date=body.end)
    db.add(p); db.flush()
    log_action(db, actor=user.username, action="MASTER_PROJECT_CREATE",
               target=f"project:{p.id}", payload={"code": body.code, "name": body.name})
    db.commit()
    return {"id": p.id, "code": p.code}


@router.get("")
async def list_projects(db: Session = Depends(get_db), user: CurrentUser = Depends(current_user)):
    return [{"id": p.id, "code": p.code, "name": p.name, "status": p.status}
            for p in db.query(Project).all()]
