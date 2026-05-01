from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.shortcuts import get_object_or_404, redirect, render
from django.utils.translation import gettext as _

from .forms import DocumentRegistrationForm
from .models import Document
from .services import register_document


@login_required
def register(request):
    q = request.GET.get("q", "")
    documents = Document.objects.select_related("project_code", "document_type", "current_revision").order_by("-created_at")
    if q:
        documents = documents.filter(document_number__icontains=q) | documents.filter(title__icontains=q)
    return render(request, "documents/register.html", {"documents": documents, "q": q})


@login_required
def register_document_view(request):
    if request.method == "POST":
        form = DocumentRegistrationForm(request.POST, request.FILES)
        if form.is_valid():
            try:
                doc = register_document(
                    user=request.user,
                    project_code=form.cleaned_data["project_code"],
                    document_type=form.cleaned_data["document_type"],
                    title=form.cleaned_data["title"],
                    uploaded_file=form.cleaned_data["source_file"],
                    reason=form.cleaned_data["reason"],
                )
            except Exception as e:
                messages.error(request, _("PDF 생성 실패: %(err)s") % {"err": e})
                return render(request, "documents/register_document.html", {"form": form})
            try:
                return redirect("documents:detail", pk=doc.pk)
            except Exception as e:
                messages.error(request, _("결재 상신 실패: %(err)s") % {"err": e})
    else:
        form = DocumentRegistrationForm()
    return render(request, "documents/register_document.html", {"form": form})


@login_required
def detail(request, pk):
    document = get_object_or_404(Document.objects.select_related("project_code", "document_type", "created_by", "current_revision"), pk=pk)
    revisions = document.revisions.select_related("created_by").order_by("-revision")
    return render(request, "documents/detail.html", {"document": document, "revisions": revisions})


@login_required
def evidence_package(request, pk):
    from audit.models import AuditEvent
    from approvals.models import ApprovalTask, ElectronicSignature
    from pdfs.models import PdfConversionJob, QaException
    from viewer.models import ViewEvent

    document = get_object_or_404(Document.objects.select_related("project_code", "document_type"), pk=pk)
    revision_ids = list(document.revisions.values_list("id", flat=True))
    tasks = ApprovalTask.objects.filter(revision__document=document).select_related("assigned_to").order_by("order")
    signatures = ElectronicSignature.objects.filter(task__revision__document=document).select_related("signer")
    pdf_jobs = PdfConversionJob.objects.filter(revision__document=document)
    qa_exceptions = QaException.objects.filter(revision__document=document)
    view_events = ViewEvent.objects.filter(revision__document=document).select_related("user").order_by("-viewed_at")
    audit_events = AuditEvent.objects.filter(object_id__in=[str(document.id)] + [str(r) for r in revision_ids]).order_by("created_at")
    return render(request, "documents/evidence_package.html", {
        "document": document,
        "tasks": tasks,
        "signatures": signatures,
        "pdf_jobs": pdf_jobs,
        "qa_exceptions": qa_exceptions,
        "view_events": view_events,
        "audit_events": audit_events,
    })
