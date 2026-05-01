import pytest
from django.contrib.auth.models import User
from django.core.files.base import ContentFile

from documents.models import DocumentRevision, DocumentStatus, DocumentType, ProjectCode


@pytest.fixture
def effective_document_factory():
    def factory():
        user = User.objects.create_user("doc_owner", password="pw")
        project = ProjectCode.objects.create(code="EFF001", name="Effective Project")
        doc_type = DocumentType.objects.create(code="EFF", name="Effective Type")
        from documents.models import Document
        document = Document.objects.create(
            document_number="EFF001-EFF-0001",
            title="Effective Document",
            project_code=project,
            document_type=doc_type,
            status=DocumentStatus.EFFECTIVE,
            created_by=user,
        )
        revision = DocumentRevision.objects.create(
            document=document,
            revision=1,
            source_file=ContentFile(b"source", name="sources/doc.docx"),
            source_filename="doc.docx",
            source_sha256="a" * 64,
            official_pdf=ContentFile(b"%PDF-1.4", name="official_pdfs/doc.pdf"),
            official_pdf_sha256="b" * 64,
            status=DocumentStatus.EFFECTIVE,
            created_by=user,
        )
        document.current_revision = revision
        document.save(update_fields=["current_revision"])
        return document
    return factory
