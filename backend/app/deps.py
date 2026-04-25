from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from app.db import SessionLocal
from app.auth.session import decode

bearer = HTTPBearer(auto_error=False)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


class CurrentUser:
    def __init__(self, username: str, role: str):
        self.username = username
        self.role = role


def current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> CurrentUser:
    if not creds:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Not authenticated")
    data = decode(creds.credentials)
    if not data:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token")
    return CurrentUser(username=data["sub"], role=data.get("role", "Author"))


def require_role(*roles: str):
    def dep(user: CurrentUser = Depends(current_user)) -> CurrentUser:
        if user.role not in roles:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Insufficient permissions")
        return user
    return dep
