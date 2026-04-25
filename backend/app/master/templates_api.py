import hashlib
import zipfile
import io
from lxml import etree
from datetime import date
from fastapi import APIRouter, Depends, Form, HTTPException, UploadFile
from fastapi.responses import Response
from sqlalchemy.orm import Session
from app.deps import get_db, require_role, current_user, CurrentUser
from app.models.master import Template, DocumentType
from app.storage.minio_client import ensure_buckets, put_object, get_object
from app.audit.log import log_action
from app.config import settings

router = APIRouter(prefix="/master/templates", tags=["master"])

CP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
VT_NS = "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"
RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types"


def inject_custom_properties(docx_bytes: bytes, props: dict[str, str]) -> bytes:
    """Inject/replace docProps/custom.xml in the docx ZIP."""
    src = io.BytesIO(docx_bytes)
    dst = io.BytesIO()
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        names = set(zin.namelist())
        # Copy all parts except ones we will rewrite
        skip = {"docProps/custom.xml", "[Content_Types].xml", "_rels/.rels"}
        for n in names:
            if n in skip:
                continue
            zout.writestr(n, zin.read(n))

        # Build custom.xml
        root = etree.Element(
            f"{{{CP_NS}}}Properties",
            nsmap={None: CP_NS, "vt": VT_NS},
        )
        for i, (k, v) in enumerate(props.items(), start=2):
            p = etree.SubElement(
                root, f"{{{CP_NS}}}property",
                fmtid="{D5CDD505-2E9C-101B-9397-08002B2CF9AE}",
                pid=str(i),
                name=k,
            )
            etree.SubElement(p, f"{{{VT_NS}}}lpwstr").text = v

        custom_xml = (
            b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            + etree.tostring(root, xml_declaration=False)
        )
        zout.writestr("docProps/custom.xml", custom_xml)

        # Patch [Content_Types].xml to include custom.xml Override if not present
        if "[Content_Types].xml" in names:
            ct_tree = etree.fromstring(zin.read("[Content_Types].xml"))
            # Check if custom.xml override already exists
            existing = ct_tree.findall(
                f"{{{CT_NS}}}Override[@PartName='/docProps/custom.xml']"
            )
            if not existing:
                etree.SubElement(
                    ct_tree,
                    f"{{{CT_NS}}}Override",
                    PartName="/docProps/custom.xml",
                    ContentType="application/vnd.openxmlformats-officedocument.custom-properties+xml",
                )
            # Write the (possibly updated) content types back
            zout.writestr(
                "[Content_Types].xml",
                b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
                + etree.tostring(ct_tree, xml_declaration=False),
            )

        # Patch _rels/.rels to add relationship to custom.xml if not present
        if "_rels/.rels" in names:
            rels_tree = etree.fromstring(zin.read("_rels/.rels"))
            existing_rels = rels_tree.findall(
                f"{{{RELS_NS}}}Relationship[@Target='docProps/custom.xml']"
            )
            if not existing_rels:
                # Find max existing Id
                ids = [
                    int(r.get("Id", "rId0").replace("rId", "0") or "0")
                    for r in rels_tree.findall(f"{{{RELS_NS}}}Relationship")
                ]
                next_id = f"rId{max(ids, default=0) + 1}"
                etree.SubElement(
                    rels_tree,
                    f"{{{RELS_NS}}}Relationship",
                    Id=next_id,
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/custom-properties",
                    Target="docProps/custom.xml",
                )
            zout.writestr(
                "_rels/.rels",
                b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
                + etree.tostring(rels_tree, xml_declaration=False),
            )

    return dst.getvalue()


def read_custom_properties(docx_bytes: bytes) -> dict[str, str]:
    """Extract custom properties from a docx."""
    with zipfile.ZipFile(io.BytesIO(docx_bytes)) as z:
        if "docProps/custom.xml" not in z.namelist():
            return {}
        root = etree.fromstring(z.read("docProps/custom.xml"))
        out = {}
        for p in root:
            name = p.get("name")
            children = list(p)
            v = children[0].text if children else None
            if name:
                out[name] = v or ""
        return out


@router.post("", status_code=201)
async def upload_template(
    file: UploadFile,
    doc_type_code: str = Form(...),
    version: str = Form(...),
    effective_date: date = Form(...),
    user: CurrentUser = Depends(require_role("Admin")),
    db: Session = Depends(get_db),
):
    dt = db.query(DocumentType).filter_by(code=doc_type_code).first()
    if not dt:
        raise HTTPException(404, "Document type not found")

    raw = await file.read()
    if not raw[:4] == b"PK\x03\x04":
        raise HTTPException(400, "File must be a valid .docx (ZIP) file")

    t = Template(doc_type_id=dt.id, version=version, effective_date=effective_date,
                 object_key="", sha256="")
    db.add(t)
    db.flush()  # assign ID

    injected = inject_custom_properties(raw, {"templateId": str(t.id), "templateVersion": version})
    sha = hashlib.sha256(injected).hexdigest()
    key = f"templates/{t.id}-{version}.docx"

    ensure_buckets()
    put_object(settings.BUCKET_TEMPLATES, key, injected,
               "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

    t.object_key = key
    t.sha256 = sha
    log_action(db, actor=user.username, action="TEMPLATE_REGISTER",
               target=f"template:{t.id}",
               payload={"doc_type": doc_type_code, "version": version, "sha256": sha})
    db.commit()
    return {"template_id": t.id, "version": version, "sha256": sha}


@router.get("/{template_id}/download")
async def download_template(
    template_id: int,
    user: CurrentUser = Depends(current_user),
    db: Session = Depends(get_db),
):
    t = db.get(Template, template_id)
    if not t:
        raise HTTPException(404, "Template not found")
    data = get_object(settings.BUCKET_TEMPLATES, t.object_key)
    return Response(
        content=data,
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        headers={"Content-Disposition": f"attachment; filename=template-{template_id}.docx"},
    )
