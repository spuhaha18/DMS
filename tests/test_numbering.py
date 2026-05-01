import pytest
from django.contrib.auth.models import User
from django.core.files.uploadedfile import SimpleUploadedFile

from audit.models import AuditEvent
from documents.models import Document, DocumentNumberSequence, DocumentStatus, DocumentType, ProjectCode
from documents.services import register_document


@pytest.mark.django_db
def test_document_numbers_are_not_reused_after_rejection_or_cancel():
    user = User.objects.create_user("researcher", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")

    first = register_document(user=user, project_code=project, document_type=doc_type, title="First", uploaded_file=SimpleUploadedFile("a.docx", b"alpha"), reason="first")
    first.status = DocumentStatus.CANCELLED
    first.save()
    second = register_document(user=user, project_code=project, document_type=doc_type, title="Second", uploaded_file=SimpleUploadedFile("b.docx", b"beta"), reason="second")

    assert first.document_number == "P001-AM-0001"
    assert second.document_number == "P001-AM-0002"
    assert DocumentNumberSequence.objects.get(project_code=project, document_type=doc_type).next_number == 3


@pytest.mark.django_db
def test_number_reservation_and_audit_event_commit_together():
    user = User.objects.create_user("researcher", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="VAL", name="Validation")

    document = register_document(user=user, project_code=project, document_type=doc_type, title="Validation", uploaded_file=SimpleUploadedFile("v.docx", b"body"), reason="registration")

    assert Document.objects.filter(document_number="P001-VAL-0001").exists()
    assert AuditEvent.objects.filter(event_type="document.registered", object_id=str(document.id)).exists()
