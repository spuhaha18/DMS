from django.contrib import admin
from django.utils.translation import gettext_lazy as _

admin.site.site_header = _("EDMS 관리자")
admin.site.site_title = _("EDMS")
admin.site.index_title = _("운영 콘솔")

from audit.services import append_event
from .models import Document, DocumentNumberSequence, DocumentRevision, DocumentType, ProjectCode


@admin.register(ProjectCode)
class ProjectCodeAdmin(admin.ModelAdmin):
    list_display = ["code", "name", "is_active", "created_at"]
    search_fields = ["code", "name"]
    list_filter = ["is_active"]

    def save_model(self, request, obj, form, change):
        before = {}
        if change and obj.pk:
            try:
                old = ProjectCode.objects.get(pk=obj.pk)
                before = {"code": old.code, "name": old.name, "is_active": old.is_active}
            except ProjectCode.DoesNotExist:
                pass
        super().save_model(request, obj, form, change)
        append_event(
            actor=request.user,
            event_type="config.changed",
            object_type="ProjectCode",
            object_id=str(obj.pk),
            before=before,
            after={"code": obj.code, "name": obj.name, "is_active": obj.is_active},
            reason="admin config change",
        )


@admin.register(DocumentType)
class DocumentTypeAdmin(admin.ModelAdmin):
    list_display = ["code", "name", "is_active"]
    search_fields = ["code", "name"]
    list_filter = ["is_active"]

    def save_model(self, request, obj, form, change):
        before = {}
        if change and obj.pk:
            try:
                old = DocumentType.objects.get(pk=obj.pk)
                before = {"code": old.code, "name": old.name, "is_active": old.is_active}
            except DocumentType.DoesNotExist:
                pass
        super().save_model(request, obj, form, change)
        append_event(
            actor=request.user,
            event_type="config.changed",
            object_type="DocumentType",
            object_id=str(obj.pk),
            before=before,
            after={"code": obj.code, "name": obj.name, "is_active": obj.is_active},
            reason="admin config change",
        )


@admin.register(DocumentNumberSequence)
class DocumentNumberSequenceAdmin(admin.ModelAdmin):
    list_display = ["project_code", "document_type", "next_number"]


@admin.register(Document)
class DocumentAdmin(admin.ModelAdmin):
    list_display = ["document_number", "title", "status", "project_code", "document_type", "created_at"]
    search_fields = ["document_number", "title"]
    list_filter = ["status", "project_code", "document_type"]
    readonly_fields = ["document_number", "created_at", "updated_at"]


@admin.register(DocumentRevision)
class DocumentRevisionAdmin(admin.ModelAdmin):
    list_display = ["document", "revision", "status", "source_sha256", "created_at"]
    search_fields = ["document__document_number"]
    list_filter = ["status"]
    readonly_fields = ["source_sha256", "official_pdf_sha256", "created_at"]
