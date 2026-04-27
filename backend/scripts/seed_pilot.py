"""Seed pilot environment: master data, users, template (with sample .docx).

Idempotent — safe to re-run. Writes a sample .docx file ready for browser submit
to ./pilot_sample.docx.
"""
import io
import sys
import zipfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import jwt
from lxml import etree

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings
from app.db import SessionLocal
from app.master.templates_api import inject_custom_properties
from app.models.approval import ApprovalTemplate
from app.models.master import DocumentType, Organization, Project, Template, User
from app.storage.minio_client import ensure_buckets, put_object


def _minimal_docx() -> bytes:
    """Build a minimal valid .docx ZIP — enough for ZIP detection + later injection."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(
            "[Content_Types].xml",
            b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            b'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            b'<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            b'<Default Extension="xml" ContentType="application/xml"/>'
            b'<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
            b"</Types>",
        )
        z.writestr(
            "_rels/.rels",
            b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            b'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            b'<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
            b"</Relationships>",
        )
        z.writestr(
            "word/document.xml",
            b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            b'<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            b'<w:body><w:p><w:r><w:t>DMS Pilot Sample Document</w:t></w:r></w:p></w:body>'
            b"</w:document>",
        )
    return buf.getvalue()


def _get_or_create(db, model, lookup: dict, defaults: dict | None = None):
    obj = db.query(model).filter_by(**lookup).first()
    if obj:
        return obj, False
    obj = model(**lookup, **(defaults or {}))
    db.add(obj)
    db.flush()
    return obj, True


def main():
    ensure_buckets()
    db = SessionLocal()
    try:
        org, _ = _get_or_create(
            db, Organization, {"code": "QA-DEPT"}, {"name": "QA Department"}
        )
        users_spec = [
            ("qa_admin", "Admin"),
            ("author1", "Author"),
            ("reviewer1", "Reviewer"),
            ("approver1", "Approver"),
        ]
        for username, role in users_spec:
            _get_or_create(
                db,
                User,
                {"username": username},
                {"role": role, "active": True, "org_id": org.id, "email": f"{username}@example.com"},
            )

        proj, _ = _get_or_create(
            db,
            Project,
            {"code": "PILOT"},
            {
                "name": "Pilot Project",
                "owner_username": "author1",
                "start_date": date(2026, 1, 1),
                "end_date": date(2027, 12, 31),
                "status": "active",
            },
        )

        dt, _ = _get_or_create(
            db,
            DocumentType,
            {"code": "SOP-PILOT"},
            {
                "name": "SOP (Pilot)",
                "numbering_pattern": "{project_code}-SOP-{seq:04d}",
                "allowed_change_types": ["New", "Revise", "Reaffirm", "Withdraw", "TempRevise"],
                "template_version_policy": "reject",
            },
        )

        at, _ = _get_or_create(
            db,
            ApprovalTemplate,
            {"doc_type_id": dt.id, "name": "Pilot 3-role"},
            {"config": {"reviewers": ["reviewer1"], "approvers": ["approver1"]}},
        )
        if dt.default_approval_template_id != at.id:
            dt.default_approval_template_id = at.id
            db.flush()

        existing = (
            db.query(Template)
            .filter_by(doc_type_id=dt.id, version="1.0")
            .first()
        )
        if existing:
            tpl = existing
            print(f"[OK] Template already exists: id={tpl.id} version={tpl.version}")
        else:
            tpl = Template(
                doc_type_id=dt.id, version="1.0",
                effective_date=date(2026, 1, 1),
                object_key="", sha256="",
            )
            db.add(tpl)
            db.flush()
            injected = inject_custom_properties(
                _minimal_docx(),
                {"templateId": str(tpl.id), "templateVersion": "1.0"},
            )
            import hashlib
            tpl.object_key = f"templates/{tpl.id}-1.0.docx"
            tpl.sha256 = hashlib.sha256(injected).hexdigest()
            put_object(
                settings.BUCKET_TEMPLATES, tpl.object_key, injected,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
            print(f"[OK] Template created: id={tpl.id}, object_key={tpl.object_key}")

        # Always (re)write sample docx so file is fresh
        injected = inject_custom_properties(
            _minimal_docx(),
            {"templateId": str(tpl.id), "templateVersion": "1.0"},
        )
        out_path = Path(__file__).resolve().parents[1] / "pilot_sample.docx"
        out_path.write_bytes(injected)
        print(f"[OK] Sample docx written: {out_path}")

        db.commit()

        # Issue 24h JWTs for browser testing
        print("\n=== JWTs (24h) ===")
        for username, role in users_spec:
            payload = {
                "sub": username,
                "role": role,
                "exp": datetime.now(timezone.utc) + timedelta(hours=24),
            }
            token = jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)
            print(f"{username:10s} ({role:8s}): {token}")

        print("\n=== Pilot setup summary ===")
        print(f"  Project       : {proj.code} ({proj.name})")
        print(f"  DocumentType  : {dt.code} ({dt.name})")
        print(f"  Approval      : reviewers=[reviewer1] approvers=[approver1]")
        print(f"  Template      : id={tpl.id} version={tpl.version}")
        print(f"  Sample file   : backend/pilot_sample.docx")
    finally:
        db.close()


if __name__ == "__main__":
    main()
