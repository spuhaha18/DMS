import zipfile
import io
from lxml import etree
from sqlalchemy.orm import Session
from app.models.master import Template, DocumentType


def read_template_props(docx_bytes: bytes) -> dict:
    with zipfile.ZipFile(io.BytesIO(docx_bytes)) as z:
        if "docProps/custom.xml" not in z.namelist():
            return {}
        root = etree.fromstring(z.read("docProps/custom.xml"))
        out = {}
        for p in root:
            name = p.get("name")
            v = p[0].text if len(p) else None
            if name:
                out[name] = v
        return out


def validate_template(db: Session, docx_bytes: bytes, doc_type: DocumentType) -> Template:
    props = read_template_props(docx_bytes)
    tid = props.get("templateId")
    ver = props.get("templateVersion")
    if not tid or not ver:
        raise ValueError("docx에 templateId/templateVersion이 없습니다. 등록된 양식을 사용하세요.")
    t = db.get(Template, int(tid))
    if not t or t.doc_type_id != doc_type.id:
        raise ValueError("templateId가 doc_type과 일치하지 않습니다.")
    if t.version != ver:
        if doc_type.template_version_policy == "reject":
            raise ValueError(f"양식 버전 불일치: 등록={t.version}, 첨부={ver}")
    return t
