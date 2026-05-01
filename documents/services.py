import hashlib
from pathlib import Path

from django.core.exceptions import ValidationError
from django.db import transaction
from django.utils import timezone

from audit.services import append_event
from documents.models import Document, DocumentNumberSequence, DocumentRevision, DocumentStatus


def _sha256(uploaded_file) -> str:
    hasher = hashlib.sha256()
    for chunk in uploaded_file.chunks():
        hasher.update(chunk)
    uploaded_file.seek(0)
    return hasher.hexdigest()


def _validate_doc_extension(filename: str) -> None:
    if Path(filename).suffix.lower() not in {".doc", ".docx"}:
        raise ValidationError("Only .doc and .docx files are accepted.")


@transaction.atomic
def register_document(*, user, project_code, document_type, title: str, uploaded_file, reason: str) -> Document:
    _validate_doc_extension(uploaded_file.name)
    sequence, _created = DocumentNumberSequence.objects.select_for_update().get_or_create(
        project_code=project_code,
        document_type=document_type,
        defaults={"next_number": 1},
    )
    serial = sequence.next_number
    sequence.next_number = serial + 1
    sequence.save(update_fields=["next_number"])
    prefix = document_type.numbering_prefix_template.format(project_code=project_code.code, document_type=document_type.code)
    document_number = f"{prefix}{serial:04d}"
    document = Document.objects.create(
        document_number=document_number,
        title=title,
        project_code=project_code,
        document_type=document_type,
        status=DocumentStatus.DRAFT_UPLOADED,
        created_by=user,
    )
    revision = DocumentRevision.objects.create(
        document=document,
        revision=1,
        source_file=uploaded_file,
        source_filename=uploaded_file.name,
        source_sha256=_sha256(uploaded_file),
        status=DocumentStatus.DRAFT_UPLOADED,
        created_by=user,
    )
    document.current_revision = revision
    document.save(update_fields=["current_revision"])
    append_event(
        actor=user,
        event_type="document.registered",
        object_type="Document",
        object_id=str(document.id),
        after={"document_number": document.document_number, "status": document.status, "revision": revision.revision},
        reason=reason,
    )
    return document


@transaction.atomic
def mark_effective(revision: DocumentRevision, *, actor, reason: str) -> DocumentRevision:
    if not revision.official_pdf or not revision.official_pdf_sha256:
        raise ValidationError("Effective revision requires an official PDF and PDF hash.")
    if revision.status != DocumentStatus.APPROVED:
        raise ValidationError("Effective revision must be approved first.")
    before = {"status": revision.status}
    revision.status = DocumentStatus.EFFECTIVE
    revision.effective_at = timezone.now()
    revision.save(update_fields=["status", "effective_at"])
    document = revision.document
    document.status = DocumentStatus.EFFECTIVE
    document.current_revision = revision
    document.save(update_fields=["status", "current_revision", "updated_at"])
    append_event(actor=actor, event_type="document.effective", object_type="DocumentRevision", object_id=str(revision.id), before=before, after={"status": revision.status}, reason=reason)
    return revision
