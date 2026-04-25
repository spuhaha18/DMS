from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.deps import get_db, require_role, current_user, CurrentUser
from app.models.master import User
from app.audit.log import log_action

router = APIRouter(prefix="/master/users", tags=["master"])


class UserIn(BaseModel):
    username: str
    email: str | None = None
    employee_no: str | None = None
    org_id: int | None = None
    title: str | None = None
    role: str = "Author"


class UserOut(BaseModel):
    id: int
    username: str


@router.post("", status_code=201, response_model=UserOut)
async def create(body: UserIn, user: CurrentUser = Depends(require_role("Admin")), db: Session = Depends(get_db)):
    if db.query(User).filter_by(username=body.username).first():
        raise HTTPException(409, "Duplicate username")
    u = User(username=body.username, email=body.email, employee_no=body.employee_no,
             org_id=body.org_id, title=body.title, role=body.role)
    db.add(u); db.flush()
    log_action(db, actor=user.username, action="MASTER_USER_CREATE",
               target=f"user:{u.id}", payload={"username": body.username, "role": body.role})
    db.commit()
    return {"id": u.id, "username": u.username}


@router.get("")
async def list_users(db: Session = Depends(get_db), user: CurrentUser = Depends(current_user)):
    return [{"id": u.id, "username": u.username, "role": u.role, "active": u.active}
            for u in db.query(User).filter_by(active=True).all()]
