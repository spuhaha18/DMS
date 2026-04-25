from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.auth import ldap, session
from app.audit.log import log_action
from app.deps import get_db, current_user, CurrentUser

router = APIRouter(prefix="/auth", tags=["auth"])


class LoginIn(BaseModel):
    username: str
    password: str


@router.post("/login")
async def login(body: LoginIn, db: Session = Depends(get_db)):
    user = ldap.authenticate(body.username, body.password)
    if not user:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            "Invalid credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )
    log_action(db, actor=user["username"], action="LOGIN", target=user["username"], payload={})
    db.commit()
    role = ldap.derive_role(user["groups"])
    token = session.issue(user["username"], role=role)
    return {"access_token": token, "token_type": "bearer", "user": user}


@router.get("/me")
async def me(user: CurrentUser = Depends(current_user)):
    return {"username": user.username, "role": user.role}
