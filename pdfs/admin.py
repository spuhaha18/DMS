from django.contrib import admin

from .models import PdfConversionJob, QaException


@admin.register(PdfConversionJob)
class PdfConversionJobAdmin(admin.ModelAdmin):
    list_display = ["revision", "status", "converter_version", "created_at", "completed_at"]
    list_filter = ["status"]


@admin.register(QaException)
class QaExceptionAdmin(admin.ModelAdmin):
    list_display = ["revision", "exception_type", "is_resolved", "created_at"]
    list_filter = ["exception_type", "is_resolved"]
