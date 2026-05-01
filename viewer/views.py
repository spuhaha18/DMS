from django.contrib.auth.decorators import login_required
from django.shortcuts import get_object_or_404, render

from documents.models import Document
from viewer.services import record_view, resolve_viewable_revision


@login_required
def pdf_view(request, document_pk, revision_id=None):
    document = get_object_or_404(Document, pk=document_pk)
    revision = resolve_viewable_revision(request.user, document, revision_id=revision_id)
    ip = request.META.get("REMOTE_ADDR")
    ua = request.META.get("HTTP_USER_AGENT", "")
    record_view(request.user, revision, ip_address=ip, user_agent=ua)
    return render(request, "viewer/pdf_view.html", {"document": document, "revision": revision})
