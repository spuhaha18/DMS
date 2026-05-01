from django.conf import settings
from django.db import models


class ViewEvent(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    revision = models.ForeignKey("documents.DocumentRevision", on_delete=models.PROTECT, related_name="view_events")
    official_pdf_sha256 = models.CharField(max_length=64)
    viewed_at = models.DateTimeField(auto_now_add=True)
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    user_agent = models.TextField(blank=True)
