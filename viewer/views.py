from django.contrib.auth.decorators import login_required
from django.http import FileResponse, Http404
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


@login_required
def pdf_stream(request, document_pk, revision_id=None):
    """Serve the official PDF through Django so every access is auth-gated."""
    document = get_object_or_404(Document, pk=document_pk)
    revision = resolve_viewable_revision(request.user, document, revision_id=revision_id)
    if not revision.official_pdf:
        raise Http404("Official PDF not available.")
    response = FileResponse(revision.official_pdf.open("rb"), content_type="application/pdf")
    response["Content-Disposition"] = "inline"
    return response
