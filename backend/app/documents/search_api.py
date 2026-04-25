from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.orm import Session
from app.deps import get_db, current_user, CurrentUser

router = APIRouter(prefix="/documents", tags=["search"])


@router.get("/search")
def search_documents(
    q: str = Query(..., min_length=1),
    effective_only: bool = Query(False),
    project_code: str | None = Query(None),
    limit: int = Query(50, le=200),
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    sql = """
        SELECT d.id, d.doc_number, d.revision, d.title, d.author_username,
               d.effective_status, d.effective_date,
               ts_rank(d.search_tsv, plainto_tsquery('simple', :q)) AS rank,
               similarity(d.doc_number || ' ' || d.title, :q) AS sim
        FROM documents d
        JOIN projects p ON p.id = d.project_id
        WHERE (
            d.search_tsv @@ plainto_tsquery('simple', :q)
            OR d.doc_number ILIKE '%' || :q || '%'
            OR d.title ILIKE '%' || :q || '%'
        )
    """
    params: dict = {"q": q, "limit": limit}
    if effective_only:
        sql += " AND d.effective_status = 'Effective'"
    if project_code:
        sql += " AND p.code = :pc"
        params["pc"] = project_code
    sql += " ORDER BY rank DESC, sim DESC LIMIT :limit"

    rows = db.execute(text(sql), params).mappings().all()
    return {"results": [dict(r) for r in rows]}
