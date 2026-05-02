from django.conf import settings
from django.db import models
from django.utils.translation import gettext_lazy as _


class ViewEvent(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, verbose_name=_("사용자"))
    revision = models.ForeignKey("documents.DocumentRevision", on_delete=models.PROTECT, related_name="view_events", verbose_name=_("리비전"))
    official_pdf_sha256 = models.CharField(_("공식 PDF SHA-256"), max_length=64)
    viewed_at = models.DateTimeField(_("열람일시"), auto_now_add=True)
    ip_address = models.GenericIPAddressField(_("IP 주소"), null=True, blank=True)
    user_agent = models.TextField(_("User Agent"), blank=True)

    class Meta:
        verbose_name = _("열람 이벤트")
        verbose_name_plural = _("열람 이벤트")
