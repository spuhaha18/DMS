from django.conf import settings
from django.contrib.auth.models import Group
from django.db import models

from documents.models import DocumentRevision


class ApprovalTaskStatus(models.TextChoices):
    PENDING = "Pending", "Pending"
    APPROVED = "Approved", "Approved"
    REJECTED = "Rejected", "Rejected"
    CANCELLED = "Cancelled", "Cancelled"


class ApprovalRouteTemplate(models.Model):
    document_type = models.ForeignKey("documents.DocumentType", on_delete=models.PROTECT, related_name="approval_templates")
    name = models.CharField(max_length=255)
    is_active = models.BooleanField(default=True)


class ApprovalRouteStep(models.Model):
    template = models.ForeignKey(ApprovalRouteTemplate, on_delete=models.CASCADE, related_name="steps")
    order = models.PositiveIntegerField()
    role = models.ForeignKey(Group, on_delete=models.PROTECT)
    signature_meaning = models.CharField(max_length=80)

    class Meta:
        ordering = ["order"]
        constraints = [
            models.UniqueConstraint(fields=["template", "order"], name="unique_step_order_per_template")
        ]


class ApprovalTask(models.Model):
    revision = models.ForeignKey(DocumentRevision, on_delete=models.PROTECT, related_name="approval_tasks")
    order = models.PositiveIntegerField()
    assigned_to = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    role_name = models.CharField(max_length=150)
    signature_meaning = models.CharField(max_length=80)
    status = models.CharField(max_length=24, choices=ApprovalTaskStatus.choices, default=ApprovalTaskStatus.PENDING)
    comment = models.TextField(blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    route_template_name = models.CharField(max_length=255)

    class Meta:
        ordering = ["order", "id"]


class ElectronicSignature(models.Model):
    task = models.OneToOneField(ApprovalTask, on_delete=models.PROTECT, related_name="signature")
    signer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    meaning = models.CharField(max_length=80)
    signed_at = models.DateTimeField(auto_now_add=True)
    source_sha256 = models.CharField(max_length=64)
    auth_method = models.CharField(max_length=80, default="password_reentry")
