from django.contrib import admin

from .models import AuditEvent


@admin.register(AuditEvent)
class AuditEventAdmin(admin.ModelAdmin):
    list_display = ["created_at", "actor", "event_type", "object_type", "object_id", "reason"]
    search_fields = ["event_type", "object_type", "object_id", "actor__username"]
    list_filter = ["event_type", "object_type"]
    readonly_fields = ["prev_hash", "event_hash", "created_at"]
