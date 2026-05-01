from django.conf import settings
from django.db import models


class AuditEvent(models.Model):
    actor = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.PROTECT)
    event_type = models.CharField(max_length=120)
    object_type = models.CharField(max_length=120)
    object_id = models.CharField(max_length=120)
    before = models.JSONField(default=dict, blank=True)
    after = models.JSONField(default=dict, blank=True)
    reason = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    prev_hash = models.CharField(max_length=64, blank=True, editable=False)
    event_hash = models.CharField(max_length=64, unique=True, editable=False)

    class Meta:
        ordering = ["created_at", "id"]

    def save(self, *args, **kwargs):
        if self.pk and not kwargs.pop("_allow_update", False):
            raise ValueError("AuditEvent is append-only and cannot be updated.")
        super().save(*args, **kwargs)
