from django.conf import settings
from django.core.exceptions import ValidationError
from django.db import models
from django.utils.translation import gettext_lazy as _


class DocumentStatus(models.TextChoices):
    RESERVED = "Reserved", _("예약됨")
    DRAFT_UPLOADED = "Draft Uploaded", _("초안 업로드")
    IN_REVIEW = "In Review", _("결재 진행중")
    REJECTED = "Rejected", _("반려됨")
    CANCELLED = "Cancelled", _("취소됨")
    APPROVED = "Approved", _("승인됨")
    EFFECTIVE = "Effective", _("발효됨")
    SUPERSEDED = "Superseded", _("대체됨")
    RETIRED = "Retired", _("폐기됨")


class ProjectCode(models.Model):
    code = models.CharField(_("프로젝트 코드"), max_length=32, unique=True)
    name = models.CharField(_("프로젝트명"), max_length=255)
    is_active = models.BooleanField(_("사용 여부"), default=True)
    created_at = models.DateTimeField(_("생성일"), auto_now_add=True)

    class Meta:
        verbose_name = _("프로젝트 코드")
        verbose_name_plural = _("프로젝트 코드")

    def __str__(self) -> str:
        return self.code


class DocumentType(models.Model):
    code = models.CharField(_("문서 유형 코드"), max_length=32, unique=True)
    name = models.CharField(_("문서 유형명"), max_length=255)
    is_active = models.BooleanField(_("사용 여부"), default=True)
    numbering_prefix_template = models.CharField(_("번호 접두 템플릿"), max_length=120, default="{project_code}-{document_type}-")

    class Meta:
        verbose_name = _("문서 유형")
        verbose_name_plural = _("문서 유형")

    def __str__(self) -> str:
        return self.code


class DocumentNumberSequence(models.Model):
    project_code = models.ForeignKey(ProjectCode, on_delete=models.PROTECT, verbose_name=_("프로젝트 코드"))
    document_type = models.ForeignKey(DocumentType, on_delete=models.PROTECT, verbose_name=_("문서 유형"))
    next_number = models.PositiveIntegerField(_("다음 번호"), default=1)

    class Meta:
        verbose_name = _("문서번호 시퀀스")
        verbose_name_plural = _("문서번호 시퀀스")
        constraints = [
            models.UniqueConstraint(fields=["project_code", "document_type"], name="unique_sequence_per_project_type")
        ]


class Document(models.Model):
    document_number = models.CharField(_("문서번호"), max_length=120, unique=True)
    title = models.CharField(_("제목"), max_length=255)
    project_code = models.ForeignKey(ProjectCode, on_delete=models.PROTECT, verbose_name=_("프로젝트 코드"))
    document_type = models.ForeignKey(DocumentType, on_delete=models.PROTECT, verbose_name=_("문서 유형"))
    status = models.CharField(_("상태"), max_length=32, choices=DocumentStatus.choices, default=DocumentStatus.RESERVED)
    current_revision = models.ForeignKey("DocumentRevision", null=True, blank=True, on_delete=models.PROTECT, related_name="+", verbose_name=_("현재 리비전"))
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, verbose_name=_("작성자"))
    created_at = models.DateTimeField(_("생성일"), auto_now_add=True)
    updated_at = models.DateTimeField(_("수정일"), auto_now=True)

    class Meta:
        verbose_name = _("문서")
        verbose_name_plural = _("문서")

    def clean(self) -> None:
        if self.status == DocumentStatus.EFFECTIVE:
            revision = self.current_revision
            if revision is None or revision.status != DocumentStatus.EFFECTIVE:
                raise ValidationError(_("발효 상태 문서는 발효된 현재 리비전이 필요합니다."))

    def __str__(self) -> str:
        return self.document_number


class DocumentRevision(models.Model):
    document = models.ForeignKey(Document, on_delete=models.PROTECT, related_name="revisions", verbose_name=_("문서"))
    revision = models.PositiveIntegerField(_("리비전 번호"))
    source_file = models.FileField(_("원본 파일"), upload_to="sources/%Y/%m/")
    source_filename = models.CharField(_("원본 파일명"), max_length=255)
    source_sha256 = models.CharField(_("원본 SHA-256"), max_length=64)
    official_pdf = models.FileField(_("공식 PDF"), upload_to="official_pdfs/%Y/%m/", blank=True)
    official_pdf_sha256 = models.CharField(_("공식 PDF SHA-256"), max_length=64, blank=True)
    status = models.CharField(_("상태"), max_length=32, choices=DocumentStatus.choices, default=DocumentStatus.DRAFT_UPLOADED)
    effective_at = models.DateTimeField(_("발효일시"), null=True, blank=True)
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, verbose_name=_("작성자"))
    created_at = models.DateTimeField(_("생성일"), auto_now_add=True)

    class Meta:
        verbose_name = _("리비전")
        verbose_name_plural = _("리비전")
        constraints = [
            models.UniqueConstraint(fields=["document", "revision"], name="unique_revision_per_document")
        ]
