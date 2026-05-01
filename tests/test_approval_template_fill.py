from datetime import datetime, timezone

import pytest
from django.contrib.auth.models import User
from django.core.files.uploadedfile import SimpleUploadedFile
from django.utils import timezone as djtz

from approvals.models import ApprovalTask, ApprovalTaskStatus, ElectronicSignature
from documents.models import DocumentStatus, DocumentType, ProjectCode
from documents.services import register_document
from pdfs.services import _build_approval_context


@pytest.mark.django_db
def test_build_approval_context_includes_signed_name_and_date(settings, tmp_path):
    settings.EDMS_PRIVATE_MEDIA_ROOT = tmp_path
    qa = User.objects.create_user("qa", password="pw")
    approver = User.objects.create_user("alice", password="pw", first_name="앨리스", last_name="김")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    document = register_document(
        user=qa, project_code=project, document_type=doc_type,
        title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", b"src"),
        reason="reg",
    )
    revision = document.current_revision
    task = ApprovalTask.objects.create(
        revision=revision, order=1, assigned_to=approver,
        role_name="Reviewer", signature_meaning="검토",
        status=ApprovalTaskStatus.APPROVED, route_template_name="default",
        completed_at=djtz.now(),
    )
    ElectronicSignature.objects.create(
        task=task, signer=approver, meaning="검토", source_sha256="a" * 64,
    )

    ctx = _build_approval_context(revision)

    assert ctx["document_number"] == document.document_number
    assert ctx["document_title"] == "Doc"
    assert ctx["revision"] == revision.revision
    assert len(ctx["approvers"]) == 1
    assert ctx["approvers"][0]["name"] == "김 앨리스"
    assert ctx["approvers"][0]["meaning"] == "검토"
    assert ctx["approvers"][0]["signed_at"]  # non-empty timestamp
    assert ctx["approver_1_name"] == "김 앨리스"
    assert ctx["approver_1_signed_at"] == ctx["approvers"][0]["signed_at"]


@pytest.mark.django_db
def test_build_approval_context_handles_unsigned_tasks(settings, tmp_path):
    settings.EDMS_PRIVATE_MEDIA_ROOT = tmp_path
    qa = User.objects.create_user("qa", password="pw")
    approver = User.objects.create_user("bob", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    document = register_document(
        user=qa, project_code=project, document_type=doc_type,
        title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", b"src"),
        reason="reg",
    )
    revision = document.current_revision
    ApprovalTask.objects.create(
        revision=revision, order=1, assigned_to=approver,
        role_name="Reviewer", signature_meaning="검토",
        status=ApprovalTaskStatus.PENDING, route_template_name="default",
    )

    ctx = _build_approval_context(revision)

    assert ctx["approvers"][0]["name"] == ""
    assert ctx["approvers"][0]["signed_at"] == ""
    assert ctx["approver_1_name"] == ""


from pathlib import Path
from unittest.mock import MagicMock, patch

from pdfs.services import generate_official_pdf


@pytest.mark.django_db
def test_generate_official_pdf_renders_docx_template_with_approval_context(settings, tmp_path):
    settings.EDMS_PRIVATE_MEDIA_ROOT = tmp_path
    qa = User.objects.create_user("qa2", password="pw")
    approver = User.objects.create_user("alice2", password="pw", first_name="앨리스", last_name="김")
    project = ProjectCode.objects.create(code="P002", name="Project 2")
    doc_type = DocumentType.objects.create(code="AM2", name="Analysis Method")
    document = register_document(
        user=qa, project_code=project, document_type=doc_type,
        title="Doc2", uploaded_file=SimpleUploadedFile("doc.docx", b"src"),
        reason="reg",
    )
    revision = document.current_revision
    revision.status = DocumentStatus.APPROVED
    revision.save()
    task = ApprovalTask.objects.create(
        revision=revision, order=1, assigned_to=approver,
        role_name="Reviewer", signature_meaning="검토",
        status=ApprovalTaskStatus.APPROVED, route_template_name="default",
        completed_at=djtz.now(),
    )
    ElectronicSignature.objects.create(
        task=task, signer=approver, meaning="검토", source_sha256="a" * 64,
    )

    rendered_ctx = {}
    saved_paths = []

    def fake_render(self, ctx):
        rendered_ctx.update(ctx)

    def fake_save(self, path):
        saved_paths.append(Path(path))
        Path(path).write_bytes(b"FAKE_DOCX")

    with patch("pdfs.services._converter_version", return_value="LibreOffice 24.2"), \
         patch("pdfs.services._run_libreoffice_conversion", return_value=b"%PDF-1.4\nbody") as m_conv, \
         patch("docxtpl.DocxTemplate.render", new=fake_render), \
         patch("docxtpl.DocxTemplate.save", new=fake_save):
        generate_official_pdf(revision, actor=qa, reason="approved")

    # docxtpl was called with approval context
    assert rendered_ctx.get("document_number") == document.document_number
    assert rendered_ctx.get("approvers", [{}])[0].get("name") == "김 앨리스"
    # LibreOffice received the filled .docx path (not the original source)
    docx_arg = m_conv.call_args[0][0]
    assert Path(str(docx_arg)).suffix == ".docx"
    assert Path(str(docx_arg)) != Path(revision.source_file.path)


from pdfs.models import QaException as _QaException


@pytest.mark.django_db
def test_docxtpl_render_failure_creates_distinct_qa_exception(settings, tmp_path):
    settings.EDMS_PRIVATE_MEDIA_ROOT = tmp_path
    qa = User.objects.create_user("qa3", password="pw")
    project = ProjectCode.objects.create(code="P003", name="Project 3")
    doc_type = DocumentType.objects.create(code="AM3", name="Analysis Method")
    document = register_document(
        user=qa, project_code=project, document_type=doc_type,
        title="Doc3", uploaded_file=SimpleUploadedFile("doc.docx", b"src"),
        reason="reg",
    )
    revision = document.current_revision
    revision.status = DocumentStatus.APPROVED
    revision.save()

    with patch("pdfs.services._converter_version", return_value="LibreOffice 24.2"), \
         patch("docxtpl.DocxTemplate.render", side_effect=Exception("template syntax error")):
        with pytest.raises(Exception, match="template syntax error"):
            generate_official_pdf(revision, actor=qa, reason="approved")

    assert _QaException.objects.filter(
        revision=revision, exception_type="approval_template_render_failed"
    ).exists()
    assert _QaException.objects.filter(
        revision=revision, exception_type="pdf_conversion_failed"
    ).exists()
