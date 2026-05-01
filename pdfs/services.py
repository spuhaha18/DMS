import hashlib
import subprocess
import tempfile
from pathlib import Path

from django.conf import settings
from django.core.files.base import ContentFile
from django.db import transaction
from django.utils import timezone
from docxtpl import DocxTemplate

from audit.services import append_event
from documents.services import mark_effective
from pdfs.models import PdfConversionJob, PdfConversionStatus, QaException


def _converter_version() -> str:
    result = subprocess.run(
        [settings.EDMS_LIBREOFFICE_BINARY, "--version"],
        capture_output=True,
        text=True,
        timeout=30,
    )
    return result.stdout.strip()


def _run_libreoffice_conversion(docx_path: Path) -> bytes:
    binary = settings.EDMS_LIBREOFFICE_BINARY
    docx_path = Path(docx_path)
    with tempfile.TemporaryDirectory() as tmpdir:
        subprocess.run(
            [binary, "--headless", "--convert-to", "pdf", "--outdir", tmpdir, str(docx_path)],
            check=True,
            capture_output=True,
            timeout=120,
        )
        pdf_name = docx_path.stem + ".pdf"
        pdf_path = Path(tmpdir) / pdf_name
        return pdf_path.read_bytes()


def _apply_watermark(pdf_bytes: bytes, revision) -> bytes:
    try:
        import io

        from pypdf import PdfReader, PdfWriter

        reader = PdfReader(io.BytesIO(pdf_bytes))
        writer = PdfWriter()
        writer.append_pages_from_reader(reader)

        watermark_text = settings.EDMS_WATERMARK_TEMPLATE.format(
            document_number=revision.document.document_number,
            revision=revision.revision,
            status=revision.status,
            generated_at=timezone.now().strftime("%Y-%m-%d"),
        )
        writer.add_metadata({
            "/Subject": watermark_text,
            "/Creator": "EDMS v1",
        })
        out = io.BytesIO()
        writer.write(out)
        return out.getvalue()
    except Exception:
        return pdf_bytes


def _build_approval_context(revision):
    from approvals.models import ApprovalTaskStatus
    tasks = (
        revision.approval_tasks
        .filter(status=ApprovalTaskStatus.APPROVED)
        .select_related("assigned_to")
        .prefetch_related("signature__signer")
        .order_by("order")
    )
    approvers = []
    for t in tasks:
        sig = getattr(t, "signature", None)
        if sig is not None:
            user = sig.signer
            if user.last_name and user.first_name:
                name = f"{user.last_name} {user.first_name}"
            else:
                name = user.get_full_name() or user.username
            signed_at = timezone.localtime(sig.signed_at).strftime("%Y-%m-%d %H:%M")
        else:
            name = ""
            signed_at = ""
        approvers.append({
            "order": t.order,
            "name": name,
            "meaning": t.signature_meaning,
            "signed_at": signed_at,
            "role": t.role_name,
        })

    ctx = {
        "approvers": approvers,
        "document_number": revision.document.document_number,
        "document_title": revision.document.title,
        "revision": revision.revision,
        "effective_date": timezone.localdate().strftime("%Y-%m-%d"),
    }
    for i, a in enumerate(approvers, start=1):
        ctx[f"approver_{i}_name"] = a["name"]
        ctx[f"approver_{i}_meaning"] = a["meaning"]
        ctx[f"approver_{i}_signed_at"] = a["signed_at"]
    return ctx


def generate_official_pdf(revision, *, actor, reason: str):
    # Create the job record before entering the atomic block so it survives rollback.
    job, _ = PdfConversionJob.objects.get_or_create(
        revision=revision,
        defaults={"status": PdfConversionStatus.PENDING},
    )

    exc_to_raise = None
    template_render_failed = False
    try:
        _generate_official_pdf_inner(revision, job=job, actor=actor, reason=reason)
    except _TemplateRenderError as exc:
        exc_to_raise = exc.__cause__
        template_render_failed = True
    except Exception as exc:
        exc_to_raise = exc

    if exc_to_raise is not None:
        # Update job to FAILED outside the rolled-back transaction.
        job.refresh_from_db()
        job.status = PdfConversionStatus.FAILED
        job.error_message = str(exc_to_raise)
        job.completed_at = timezone.now()
        job.save(update_fields=["status", "error_message", "completed_at"])
        if template_render_failed:
            QaException.objects.create(
                revision=revision,
                exception_type="approval_template_render_failed",
                message=str(exc_to_raise),
            )
        QaException.objects.create(
            revision=revision,
            exception_type="pdf_conversion_failed",
            message=str(exc_to_raise),
        )
        raise exc_to_raise


class _TemplateRenderError(Exception):
    """Internal sentinel: wraps a docxtpl render failure so it survives the atomic rollback."""


@transaction.atomic
def _generate_official_pdf_inner(revision, *, job, actor, reason: str):
    job.status = PdfConversionStatus.PENDING
    job.save(update_fields=["status"])

    converter_ver = _converter_version()
    job.converter_version = converter_ver
    job.save(update_fields=["converter_version"])

    source_path = Path(revision.source_file.path)
    with tempfile.TemporaryDirectory() as tmpdir:
        if source_path.suffix.lower() == ".docx":
            filled_path = Path(tmpdir) / "filled.docx"
            try:
                tpl = DocxTemplate(str(source_path))
                tpl.render(_build_approval_context(revision))
                tpl.save(str(filled_path))
            except Exception as exc:
                raise _TemplateRenderError() from exc
            conversion_input = filled_path
        else:
            conversion_input = source_path

        pdf_bytes = _run_libreoffice_conversion(conversion_input)
        pdf_bytes = _apply_watermark(pdf_bytes, revision)

    pdf_filename = f"{revision.document.document_number}_rev{revision.revision}.pdf"
    revision.official_pdf.save(pdf_filename, ContentFile(pdf_bytes), save=False)
    revision.official_pdf_sha256 = hashlib.sha256(pdf_bytes).hexdigest()
    revision.save(update_fields=["official_pdf", "official_pdf_sha256"])

    job.status = PdfConversionStatus.SUCCEEDED
    job.completed_at = timezone.now()
    job.save(update_fields=["status", "completed_at"])

    append_event(
        actor=actor,
        event_type="pdf.generated",
        object_type="DocumentRevision",
        object_id=str(revision.id),
        after={"official_pdf_sha256": revision.official_pdf_sha256, "converter_version": converter_ver},
        reason=reason,
    )

    mark_effective(revision, actor=actor, reason=reason)
