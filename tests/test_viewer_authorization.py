import pytest
from django.contrib.auth.models import Group, User
from django.core.exceptions import PermissionDenied

from documents.models import DocumentStatus
from viewer.models import ViewEvent
from viewer.services import record_view, resolve_viewable_revision


@pytest.mark.django_db
def test_regular_reader_gets_only_current_effective_revision(effective_document_factory):
    reader_group = Group.objects.create(name="Reader")
    reader = User.objects.create_user("reader", password="pw")
    reader.groups.add(reader_group)
    document = effective_document_factory()

    revision = resolve_viewable_revision(user=reader, document=document)

    assert revision == document.current_revision
    assert revision.status == DocumentStatus.EFFECTIVE


@pytest.mark.django_db
def test_regular_reader_cannot_view_historical_revision(effective_document_factory):
    reader_group = Group.objects.create(name="Reader")
    reader = User.objects.create_user("reader", password="pw")
    reader.groups.add(reader_group)
    document = effective_document_factory()
    old_revision = document.revisions.create(
        revision=2,
        source_file="sources/old.docx",
        source_filename="old.docx",
        source_sha256="a" * 64,
        official_pdf="official_pdfs/old.pdf",
        official_pdf_sha256="b" * 64,
        status=DocumentStatus.SUPERSEDED,
        created_by=document.created_by,
    )

    with pytest.raises(PermissionDenied):
        resolve_viewable_revision(user=reader, document=document, revision_id=old_revision.id)


@pytest.mark.django_db
def test_every_view_creates_view_event(effective_document_factory):
    reader = User.objects.create_user("reader", password="pw")
    document = effective_document_factory()

    event = record_view(user=reader, revision=document.current_revision, ip_address="127.0.0.1", user_agent="pytest")

    assert ViewEvent.objects.filter(id=event.id, official_pdf_sha256=document.current_revision.official_pdf_sha256).exists()
