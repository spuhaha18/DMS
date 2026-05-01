from django.contrib import admin

from .models import ViewEvent


@admin.register(ViewEvent)
class ViewEventAdmin(admin.ModelAdmin):
    list_display = ["user", "revision", "viewed_at", "ip_address"]
    search_fields = ["user__username", "revision__document__document_number"]
    readonly_fields = ["viewed_at", "official_pdf_sha256"]
