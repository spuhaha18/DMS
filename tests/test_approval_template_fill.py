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
