from django.contrib.auth.decorators import login_required
from django.core.exceptions import PermissionDenied
from django.http import FileResponse, Http404, HttpResponse
from django.shortcuts import get_object_or_404, render
from django.utils.translation import gettext as _
from django.views.decorators.clickjacking import xframe_options_sameorigin

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
@xframe_options_sameorigin
def pdf_stream(request, document_pk, revision_id=None):
    """Serve the official PDF through Django so every access is auth-gated."""
    document = get_object_or_404(Document, pk=document_pk)
    try:
        revision = resolve_viewable_revision(request.user, document, revision_id=revision_id)
    except PermissionDenied as e:
        return HttpResponse(_("접근이 거부되었습니다: %(err)s") % {"err": e}, status=403)
    if not revision.official_pdf:
        return HttpResponse(_("공식 PDF가 아직 발급되지 않았습니다."), status=404)
    try:
        response = FileResponse(revision.official_pdf.open("rb"), content_type="application/pdf")
        response["Content-Disposition"] = "inline"
        return response
    except Exception as e:
        return HttpResponse(_("PDF 로드 실패: %(err)s") % {"err": e}, status=500)
