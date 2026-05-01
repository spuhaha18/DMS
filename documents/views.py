import logging

from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.shortcuts import get_object_or_404, redirect, render
from django.utils.translation import gettext as _

from .forms import DocumentRegistrationForm
from .models import Document
from .services import register_document

logger = logging.getLogger(__name__)


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
                logger.exception("document registration failed for user %s", request.user.pk)
                messages.error(request, _("문서 등록 중 오류가 발생했습니다."))
                return render(request, "documents/register_document.html", {"form": form})
            return redirect("documents:detail", pk=doc.pk)
    else:
        form = DocumentRegistrationForm()
    return render(request, "documents/register_document.html", {"form": form})


@login_required
def detail(request, pk):
    document = get_object_or_404(Document.objects.select_related("project_code", "document_type", "created_by", "current_revision"), pk=pk)
    revisions = document.revisions.select_related("created_by").order_by("-revision")
    return render(request, "documents/detail.html", {"document": document, "revisions": revisions})


def _is_document_owner_or_qa(user, document) -> bool:
    if user.is_superuser or user.is_staff:
        return True
    if user == document.created_by:
        return True
    return user.groups.filter(name="QA").exists()


def _is_qa_or_admin(user) -> bool:
    if user.is_superuser or user.is_staff:
        return True
    return user.groups.filter(name="QA").exists()


@login_required
def submit_for_approval_view(request, pk):
    if request.method != "POST":
        from django.http import HttpResponseNotAllowed
        return HttpResponseNotAllowed(["POST"])
    document = get_object_or_404(Document.objects.select_related("created_by", "current_revision"), pk=pk)
    if not _is_document_owner_or_qa(request.user, document):
        messages.error(request, _("권한이 없습니다."))
        return redirect("documents:detail", pk=pk)
    revision = document.current_revision
    if revision is None:
        messages.error(request, _("결재 상신할 리비전이 없습니다."))
        return redirect("documents:detail", pk=pk)
    try:
        from approvals.services import submit_for_approval
        submit_for_approval(revision, actor=request.user, reason=_("문서 등록부에서 결재 상신"))
    except Exception as e:
        logger.exception("submit_for_approval failed for revision %s", revision.pk)
        messages.error(request, _("결재 상신 중 오류가 발생했습니다."))
    return redirect("documents:detail", pk=pk)


@login_required
def generate_pdf_view(request, pk):
    if request.method != "POST":
        from django.http import HttpResponseNotAllowed
        return HttpResponseNotAllowed(["POST"])
    from documents.models import DocumentStatus
    document = get_object_or_404(Document.objects.select_related("created_by", "current_revision"), pk=pk)
    if not _is_qa_or_admin(request.user):
        messages.error(request, _("권한이 없습니다."))
        return redirect("documents:detail", pk=pk)
    revision = document.current_revision
    if revision is None or revision.status != DocumentStatus.APPROVED:
        messages.error(request, _("승인된 리비전이 없어 공식 PDF를 발급할 수 없습니다."))
        return redirect("documents:detail", pk=pk)
    try:
        from pdfs.services import generate_official_pdf
        generate_official_pdf(revision, actor=request.user, reason=_("문서 등록부에서 공식 PDF 발급"))
    except Exception as e:
        logger.exception("generate_official_pdf failed for revision %s", revision.pk)
        messages.error(request, _("공식 PDF 발급 중 오류가 발생했습니다."))
    return redirect("documents:detail", pk=pk)


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
