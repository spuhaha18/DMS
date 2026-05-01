import hashlib
import pytest
from django.contrib.auth.models import User
from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile

from documents.models import DocumentStatus, DocumentType, ProjectCode
from documents.services import mark_effective, register_document


@pytest.mark.django_db
def test_only_doc_and_docx_are_accepted():
    user = User.objects.create_user("researcher", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")

    with pytest.raises(ValidationError, match="Only .doc and .docx"):
        register_document(user=user, project_code=project, document_type=doc_type, title="Bad", uploaded_file=SimpleUploadedFile("bad.pdf", b"pdf"), reason="bad")


@pytest.mark.django_db
def test_source_sha256_is_recorded():
    user = User.objects.create_user("researcher", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    content = b"source bytes"

    document = register_document(user=user, project_code=project, document_type=doc_type, title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", content), reason="registration")

    assert document.status == DocumentStatus.DRAFT_UPLOADED
    assert document.current_revision.source_sha256 == hashlib.sha256(content).hexdigest()


@pytest.mark.django_db
def test_draft_uploaded_cannot_jump_to_effective_without_pdf():
    user = User.objects.create_user("researcher", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    document = register_document(user=user, project_code=project, document_type=doc_type, title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", b"content"), reason="registration")

    with pytest.raises(ValidationError, match="official PDF"):
        mark_effective(document.current_revision, actor=user, reason="invalid")
