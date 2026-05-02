from pathlib import Path
from unittest.mock import patch

import pytest
from django.contrib.auth.models import User
from django.core.files.uploadedfile import SimpleUploadedFile

from documents.models import DocumentStatus, DocumentType, ProjectCode
from documents.services import register_document
from pdfs.models import PdfConversionJob, PdfConversionStatus, QaException
from pdfs.services import generate_official_pdf


@pytest.mark.django_db
def test_successful_conversion_records_version_hash_and_effective_status(tmp_path, settings):
    settings.EDMS_PRIVATE_MEDIA_ROOT = tmp_path
    user = User.objects.create_user("qa", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    document = register_document(user=user, project_code=project, document_type=doc_type, title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", b"source"), reason="registration")
    revision = document.current_revision
    revision.status = DocumentStatus.APPROVED
    revision.save()

    with patch("pdfs.services._converter_version", return_value="LibreOffice 24.2"), \
         patch("pdfs.services._run_libreoffice_conversion", return_value=b"%PDF-1.4\nbody") as m_conv, \
         patch("docxtpl.DocxTemplate.render"), \
         patch("docxtpl.DocxTemplate.save", side_effect=lambda path: open(path, "wb").write(b"FAKE_DOCX")):
        generate_official_pdf(revision, actor=user, reason="approved")

    assert m_conv.called
    called_arg = m_conv.call_args[0][0]
    assert str(called_arg).endswith(".docx"), f"Expected .docx path, got: {called_arg}"

    revision.refresh_from_db()
    job = PdfConversionJob.objects.get(revision=revision)
    assert job.status == PdfConversionStatus.SUCCEEDED
    assert job.converter_version == "LibreOffice 24.2"
    assert revision.official_pdf_sha256
    assert revision.status == DocumentStatus.EFFECTIVE


@pytest.mark.django_db
def test_failed_conversion_blocks_effective_and_creates_exception():
    user = User.objects.create_user("qa", password="pw")
    project = ProjectCode.objects.create(code="P001", name="Project 1")
    doc_type = DocumentType.objects.create(code="AM", name="Analysis Method")
    document = register_document(user=user, project_code=project, document_type=doc_type, title="Doc", uploaded_file=SimpleUploadedFile("doc.docx", b"source"), reason="registration")
    revision = document.current_revision
    revision.status = DocumentStatus.APPROVED
    revision.save()

    with patch("pdfs.services._converter_version", return_value="LibreOffice 24.2"), \
         patch("pdfs.services._run_libreoffice_conversion", side_effect=RuntimeError("conversion failed")), \
         patch("docxtpl.DocxTemplate.render"), \
         patch("docxtpl.DocxTemplate.save", side_effect=lambda path: open(path, "wb").write(b"FAKE_DOCX")):
        with pytest.raises(RuntimeError, match="conversion failed"):
            generate_official_pdf(revision, actor=user, reason="approved")

    revision.refresh_from_db()
    assert revision.status == DocumentStatus.APPROVED
    assert QaException.objects.filter(revision=revision, exception_type="pdf_conversion_failed").exists()
    job = PdfConversionJob.objects.get(revision=revision)
    assert job.status == PdfConversionStatus.FAILED
