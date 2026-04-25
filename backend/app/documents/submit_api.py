from datetime import datetime
from fastapi import APIRouter, Depends, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session
from app.deps import get_db, current_user, CurrentUser
from app.models.master import Project, DocumentType
from app.models.document import Document
from app.numbering.service import assign_number
from app.documents.template_validator import validate_template
from app.audit.log import log_action
from app.storage.minio_client import ensure_buckets, put_object
from app.config import settings

router = APIRouter(prefix="/documents", tags=["documents"])


@router.post("/submit", status_code=201)
async def submit_document(
    file: UploadFile,
    project_code: str = Form(...),
    doc_type_code: str = Form(...),
    change_type: str = Form(...),
    title: str = Form(...),
    parent_doc_number: str | None = Form(None),
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    pj = db.query(Project).filter_by(code=project_code).first()
    dt = db.query(DocumentType).filter_by(code=doc_type_code).first()
    if not pj or not dt:
        raise HTTPException(404, "Project or DocumentType not found")

    if change_type not in dt.allowed_change_types:
        raise HTTPException(400, f"change_type '{change_type}' not allowed for {dt.code}")

    raw = await file.read()
    if raw[:4] != b"PK\x03\x04":
        raise HTTPException(400, "File must be a valid .docx (ZIP) file")

    try:
        tpl = validate_template(db, raw, dt)
    except ValueError as e:
        raise HTTPException(400, str(e))

    parent_id = None
    relation_type = None

    if change_type == "New":
        doc_number = assign_number(db, pj.id, dt.id)
        revision = "A"
    else:
        if not parent_doc_number:
            raise HTTPException(400, "parent_doc_number required for non-New change types")
        parent = db.query(Document).filter_by(doc_number=parent_doc_number).first()
        if not parent:
            raise HTTPException(404, "parent document not found")
        parent_id = parent.id
        if change_type == "Revision":
            doc_number = parent.doc_number
            current_rev = parent.revision or "A"
            if current_rev >= "Z":
                raise HTTPException(400, "Revision letter overflow: cannot increment beyond Z")
            revision = chr(ord(current_rev) + 1)
            relation_type = "revision"
        else:
            suffix_map = {"Errata": "-E", "Addendum": "-AD", "Amendment": "-AM"}
            if change_type not in suffix_map:
                raise HTTPException(400, f"Unsupported change_type: {change_type}")
            suffix = suffix_map[change_type]
            doc_number = f"{parent.doc_number}{suffix}{datetime.utcnow():%y%m%d}"
            revision = parent.revision
            relation_type = change_type.lower()

    ensure_buckets()
    key = f"sources/{doc_number}.docx"
    put_object(settings.BUCKET_SOURCE, key, raw,
               "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

    d = Document(
        doc_number=doc_number,
        revision=revision,
        document_type_id=dt.id,
        project_id=pj.id,
        author_username=user.username,
        parent_document_id=parent_id,
        relation_type=relation_type,
        title=title,
        effective_status="Draft",
        template_id=tpl.id,
        template_version=tpl.version,
        source_docx_key=key,
    )
    db.add(d)
    db.flush()

    log_action(db, actor=user.username, action="DOC_SUBMIT",
               target=f"document:{d.id}",
               payload={"doc_number": doc_number, "change_type": change_type, "title": title})
    db.commit()
    return {"document_id": d.id, "doc_number": doc_number, "revision": revision}
