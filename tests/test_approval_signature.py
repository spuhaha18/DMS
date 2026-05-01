import pytest
from django.contrib.auth.models import Group, User
from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile

from approvals.models import ApprovalRouteStep, ApprovalRouteTemplate, ApprovalTask, ElectronicSignature
from approvals.services import approve_task, reject_task, submit_for_approval
from documents.models import DocumentStatus, DocumentType, ProjectCode
from documents.services import register_document


def _submitted_single_step_task():
    reviewer_group = Group.objects.create(name="Reviewer")
    reviewer = User.objects.create_user("reviewer", password="pw")
    reviewer.groups.add(reviewer_group)
    researcher = User.objects.create_user("researcher", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    template = ApprovalRouteTemplate.objects.create(document_type=doc_type, name="AM Route")
    ApprovalRouteStep.objects.create(template=template, order=1, role=reviewer_group, signature_meaning="Review")
    document = register_document(user=researcher, project_code=project, document_type=doc_type, title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", b"v1"), reason="registration")
    submit_for_approval(document.current_revision, actor=researcher, reason="submit")
    return ApprovalTask.objects.get(revision=document.current_revision)


@pytest.mark.django_db
def test_route_snapshot_does_not_change_when_template_changes_later():
    reviewer_group = Group.objects.create(name="Reviewer")
    reviewer = User.objects.create_user("reviewer", password="pw")
    reviewer.groups.add(reviewer_group)
    researcher = User.objects.create_user("researcher", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    template = ApprovalRouteTemplate.objects.create(document_type=doc_type, name="AM Route")
    ApprovalRouteStep.objects.create(template=template, order=1, role=reviewer_group, signature_meaning="Review")
    document = register_document(user=researcher, project_code=project, document_type=doc_type, title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", b"v1"), reason="registration")

    submit_for_approval(document.current_revision, actor=researcher, reason="submit")
    template.name = "Changed Route"
    template.save()

    task = ApprovalTask.objects.get(revision=document.current_revision)
    assert task.route_template_name == "AM Route"
    assert task.signature_meaning == "Review"


@pytest.mark.django_db
def test_password_reentry_required_before_signing():
    task = _submitted_single_step_task()

    with pytest.raises(ValidationError, match="Password re-entry failed"):
        approve_task(task, signer=task.assigned_to, password="wrong", comment="ok", reason="sign")


@pytest.mark.django_db
def test_signature_binds_to_revision_and_source_hash():
    task = _submitted_single_step_task()

    approve_task(task, signer=task.assigned_to, password="pw", comment="ok", reason="sign")
    signature = ElectronicSignature.objects.get(task=task)

    assert signature.signer == task.assigned_to
    assert signature.meaning == task.signature_meaning
    assert signature.source_sha256 == task.revision.source_sha256
    assert signature.auth_method == "password_reentry"


@pytest.mark.django_db
def test_rejection_marks_revision_rejected_and_does_not_create_signature():
    task = _submitted_single_step_task()

    reject_task(task, signer=task.assigned_to, password="pw", comment="fix", reason="reject")

    task.refresh_from_db()
    task.revision.refresh_from_db()
    assert task.status == "Rejected"
    assert task.revision.status == DocumentStatus.REJECTED
    assert not ElectronicSignature.objects.filter(task=task).exists()


@pytest.mark.django_db
def test_submit_for_approval_raises_if_already_in_review():
    task = _submitted_single_step_task()
    revision = task.revision

    with pytest.raises(ValidationError, match="already submitted"):
        submit_for_approval(revision, actor=revision.document.created_by, reason="duplicate")


@pytest.mark.django_db
def test_approve_already_approved_task_is_blocked():
    task = _submitted_single_step_task()
    approve_task(task, signer=task.assigned_to, password="pw", comment="ok", reason="sign")

    task.refresh_from_db()
    with pytest.raises(ValidationError, match="not pending"):
        approve_task(task, signer=task.assigned_to, password="pw", comment="again", reason="re-sign")
