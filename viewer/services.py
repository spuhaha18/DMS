from django.core.exceptions import PermissionDenied

from audit.services import append_event
from documents.models import DocumentStatus
from viewer.models import ViewEvent


def user_is_qa_or_admin(user) -> bool:
    if user.is_superuser or user.is_staff:
        return True
    return user.groups.filter(name="QA").exists()


def resolve_viewable_revision(user, document, revision_id=None):
    if revision_id is None:
        revision = document.current_revision
        if revision is None or revision.status != DocumentStatus.EFFECTIVE:
            raise PermissionDenied("No current effective revision available.")
        return revision

    from documents.models import DocumentRevision
    revision = DocumentRevision.objects.get(pk=revision_id, document=document)
    if not user_is_qa_or_admin(user):
        raise PermissionDenied("Only QA or admins may access historical revisions.")
    return revision


def record_view(user, revision, *, ip_address=None, user_agent="") -> ViewEvent:
    event = ViewEvent.objects.create(
        user=user,
        revision=revision,
        official_pdf_sha256=revision.official_pdf_sha256,
        ip_address=ip_address,
        user_agent=user_agent,
    )
    append_event(
        actor=user,
        event_type="viewer.pdf_viewed",
        object_type="DocumentRevision",
        object_id=str(revision.id),
        after={"official_pdf_sha256": revision.official_pdf_sha256},
        reason="controlled PDF view",
    )
    return event
