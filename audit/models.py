from django.conf import settings
from django.db import models
from django.utils.translation import gettext_lazy as _


class AuditEvent(models.Model):
    actor = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.PROTECT, verbose_name=_("실행자"))
    event_type = models.CharField(_("이벤트 유형"), max_length=120)
    object_type = models.CharField(_("대상 유형"), max_length=120)
    object_id = models.CharField(_("대상 ID"), max_length=120)
    before = models.JSONField(_("변경 전"), default=dict, blank=True)
    after = models.JSONField(_("변경 후"), default=dict, blank=True)
    reason = models.TextField(_("사유"), blank=True)
    created_at = models.DateTimeField(_("생성일시"), auto_now_add=True)
    prev_hash = models.CharField(_("이전 해시"), max_length=64, blank=True, editable=False)
    event_hash = models.CharField(_("이벤트 해시"), max_length=64, unique=True, editable=False)

    class Meta:
        verbose_name = _("감사 이벤트")
        verbose_name_plural = _("감사 이벤트")
        ordering = ["created_at", "id"]

    def save(self, *args, **kwargs):
        if self.pk and not kwargs.pop("_allow_update", False):
            raise ValueError("AuditEvent is append-only and cannot be updated.")
        super().save(*args, **kwargs)
