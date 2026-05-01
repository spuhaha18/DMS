from django.contrib import admin

from audit.services import append_event
from .models import ApprovalRouteStep, ApprovalRouteTemplate, ApprovalTask, ElectronicSignature


@admin.register(ApprovalRouteTemplate)
class ApprovalRouteTemplateAdmin(admin.ModelAdmin):
    list_display = ["name", "document_type", "is_active"]
    list_filter = ["is_active", "document_type"]

    def save_model(self, request, obj, form, change):
        before = {}
        if change and obj.pk:
            try:
                old = ApprovalRouteTemplate.objects.get(pk=obj.pk)
                before = {"name": old.name, "is_active": old.is_active}
            except ApprovalRouteTemplate.DoesNotExist:
                pass
        super().save_model(request, obj, form, change)
        append_event(
            actor=request.user,
            event_type="config.changed",
            object_type="ApprovalRouteTemplate",
            object_id=str(obj.pk),
            before=before,
            after={"name": obj.name, "is_active": obj.is_active},
            reason="admin config change",
        )


@admin.register(ApprovalRouteStep)
class ApprovalRouteStepAdmin(admin.ModelAdmin):
    list_display = ["template", "order", "role", "signature_meaning"]
    list_filter = ["template"]

    def save_model(self, request, obj, form, change):
        before = {}
        if change and obj.pk:
            try:
                old = ApprovalRouteStep.objects.get(pk=obj.pk)
                before = {"order": old.order, "signature_meaning": old.signature_meaning}
            except ApprovalRouteStep.DoesNotExist:
                pass
        super().save_model(request, obj, form, change)
        append_event(
            actor=request.user,
            event_type="config.changed",
            object_type="ApprovalRouteStep",
            object_id=str(obj.pk),
            before=before,
            after={"order": obj.order, "signature_meaning": obj.signature_meaning},
            reason="admin config change",
        )


@admin.register(ApprovalTask)
class ApprovalTaskAdmin(admin.ModelAdmin):
    list_display = ["revision", "order", "assigned_to", "role_name", "status", "completed_at"]
    list_filter = ["status"]
    search_fields = ["revision__document__document_number"]


@admin.register(ElectronicSignature)
class ElectronicSignatureAdmin(admin.ModelAdmin):
    list_display = ["task", "signer", "meaning", "signed_at", "auth_method"]
    readonly_fields = ["signed_at", "source_sha256"]
