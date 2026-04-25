from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from app.config import settings
from app.exceptions import DMSException
from app.auth.api import router as auth_router
from app.master.projects_api import router as projects_router
from app.master.document_types_api import router as doctypes_router
from app.master.organizations_api import router as orgs_router
from app.master.users_api import router as users_router
from app.master.templates_api import router as templates_router

app = FastAPI(title="DMS", version="0.1.0")
app.include_router(auth_router)
app.include_router(projects_router)
app.include_router(doctypes_router)
app.include_router(orgs_router)
app.include_router(users_router)
app.include_router(templates_router)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[settings.FRONTEND_URL],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(DMSException)
async def dms_exception_handler(request: Request, exc: DMSException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"error_code": exc.error_code, "message": exc.message, "detail": exc.detail},
    )


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/ready")
async def ready():
    results = {}
    # Postgres check
    try:
        from app.db import engine
        from sqlalchemy import text
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        results["postgres"] = "ok"
    except Exception as e:
        results["postgres"] = f"error: {e}"

    # MinIO check
    try:
        from minio import Minio
        client = Minio(settings.MINIO_ENDPOINT, settings.MINIO_ACCESS_KEY, settings.MINIO_SECRET_KEY, secure=False)
        client.bucket_exists(settings.BUCKET_FINAL)
        results["minio"] = "ok"
    except Exception as e:
        results["minio"] = f"error: {e}"

    all_ok = all(v == "ok" for v in results.values())
    return JSONResponse(
        status_code=200 if all_ok else 503,
        content={"status": "ok" if all_ok else "degraded", "checks": results},
    )
