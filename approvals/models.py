from django.conf import settings
from django.contrib.auth.models import Group
from django.db import models
from django.utils.translation import gettext_lazy as _

from documents.models import DocumentRevision


class ApprovalTaskStatus(models.TextChoices):
    PENDING = "Pending", _("대기중")
    APPROVED = "Approved", _("승인")
    REJECTED = "Rejected", _("반려")
    CANCELLED = "Cancelled", _("취소")


class ApprovalRouteTemplate(models.Model):
    document_type = models.ForeignKey("documents.DocumentType", on_delete=models.PROTECT, related_name="approval_templates", verbose_name=_("문서 유형"))
    name = models.CharField(_("템플릿명"), max_length=255)
    is_active = models.BooleanField(_("사용 여부"), default=True)

    class Meta:
        verbose_name = _("결재선 템플릿")
        verbose_name_plural = _("결재선 템플릿")


class ApprovalRouteStep(models.Model):
    template = models.ForeignKey(ApprovalRouteTemplate, on_delete=models.CASCADE, related_name="steps", verbose_name=_("결재선"))
    order = models.PositiveIntegerField(_("순서"))
    role = models.ForeignKey(Group, on_delete=models.PROTECT, verbose_name=_("역할"))
    signature_meaning = models.CharField(
        _("결재 의미"), max_length=80,
        help_text=_("\"검토\", \"승인\" 등 한국어로 입력하세요."),
    )

    class Meta:
        verbose_name = _("결재 단계")
        verbose_name_plural = _("결재 단계")
        ordering = ["order"]
        constraints = [
            models.UniqueConstraint(fields=["template", "order"], name="unique_step_order_per_template")
        ]


class ApprovalTask(models.Model):
    revision = models.ForeignKey(DocumentRevision, on_delete=models.PROTECT, related_name="approval_tasks", verbose_name=_("리비전"))
    order = models.PositiveIntegerField(_("순서"))
    assigned_to = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, verbose_name=_("지정 결재자"))
    role_name = models.CharField(_("역할명"), max_length=150)
    signature_meaning = models.CharField(_("결재 의미"), max_length=80)
    status = models.CharField(_("상태"), max_length=24, choices=ApprovalTaskStatus.choices, default=ApprovalTaskStatus.PENDING)
    comment = models.TextField(_("의견"), blank=True)
    completed_at = models.DateTimeField(_("완료일시"), null=True, blank=True)
    route_template_name = models.CharField(_("결재선명"), max_length=255)

    class Meta:
        verbose_name = _("결재 작업")
        verbose_name_plural = _("결재 작업")
        ordering = ["order", "id"]


class ElectronicSignature(models.Model):
    task = models.OneToOneField(ApprovalTask, on_delete=models.PROTECT, related_name="signature", verbose_name=_("결재 작업"))
    signer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, verbose_name=_("서명자"))
    meaning = models.CharField(_("결재 의미"), max_length=80)
    signed_at = models.DateTimeField(_("서명일시"), auto_now_add=True)
    source_sha256 = models.CharField(_("원본 SHA-256"), max_length=64)
    auth_method = models.CharField(_("인증 방식"), max_length=80, default="password_reentry")

    class Meta:
        verbose_name = _("전자서명")
        verbose_name_plural = _("전자서명")
