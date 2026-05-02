from django.db import models
from django.utils.translation import gettext_lazy as _


class PdfConversionStatus(models.TextChoices):
    PENDING = "Pending", _("대기중")
    SUCCEEDED = "Succeeded", _("성공")
    FAILED = "Failed", _("실패")


class PdfConversionJob(models.Model):
    revision = models.OneToOneField("documents.DocumentRevision", on_delete=models.PROTECT, related_name="pdf_job", verbose_name=_("리비전"))
    status = models.CharField(_("상태"), max_length=24, choices=PdfConversionStatus.choices, default=PdfConversionStatus.PENDING)
    converter_version = models.CharField(_("변환기 버전"), max_length=255, blank=True)
    error_message = models.TextField(_("오류 메시지"), blank=True)
    created_at = models.DateTimeField(_("생성일시"), auto_now_add=True)
    completed_at = models.DateTimeField(_("완료일시"), null=True, blank=True)

    class Meta:
        verbose_name = _("PDF 변환 작업")
        verbose_name_plural = _("PDF 변환 작업")


class QaException(models.Model):
    revision = models.ForeignKey("documents.DocumentRevision", on_delete=models.PROTECT, related_name="qa_exceptions", verbose_name=_("리비전"))
    exception_type = models.CharField(_("예외 유형"), max_length=120)
    message = models.TextField(_("메시지"))
    is_resolved = models.BooleanField(_("해결됨"), default=False)
    created_at = models.DateTimeField(_("생성일시"), auto_now_add=True)

    class Meta:
        verbose_name = _("QA 예외")
        verbose_name_plural = _("QA 예외")
