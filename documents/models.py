from django.conf import settings
from django.core.exceptions import ValidationError
from django.db import models


class DocumentStatus(models.TextChoices):
    RESERVED = "Reserved", "Reserved"
    DRAFT_UPLOADED = "Draft Uploaded", "Draft Uploaded"
    IN_REVIEW = "In Review", "In Review"
    REJECTED = "Rejected", "Rejected"
    CANCELLED = "Cancelled", "Cancelled"
    APPROVED = "Approved", "Approved"
    EFFECTIVE = "Effective", "Effective"
    SUPERSEDED = "Superseded", "Superseded"
    RETIRED = "Retired", "Retired"


class ProjectCode(models.Model):
    code = models.CharField(max_length=32, unique=True)
    name = models.CharField(max_length=255)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return self.code


class DocumentType(models.Model):
    code = models.CharField(max_length=32, unique=True)
    name = models.CharField(max_length=255)
    is_active = models.BooleanField(default=True)
    numbering_prefix_template = models.CharField(max_length=120, default="{project_code}-{document_type}-")

    def __str__(self) -> str:
        return self.code


class DocumentNumberSequence(models.Model):
    project_code = models.ForeignKey(ProjectCode, on_delete=models.PROTECT)
    document_type = models.ForeignKey(DocumentType, on_delete=models.PROTECT)
    next_number = models.PositiveIntegerField(default=1)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["project_code", "document_type"], name="unique_sequence_per_project_type")
        ]


class Document(models.Model):
    document_number = models.CharField(max_length=120, unique=True)
    title = models.CharField(max_length=255)
    project_code = models.ForeignKey(ProjectCode, on_delete=models.PROTECT)
    document_type = models.ForeignKey(DocumentType, on_delete=models.PROTECT)
    status = models.CharField(max_length=32, choices=DocumentStatus.choices, default=DocumentStatus.RESERVED)
    current_revision = models.ForeignKey("DocumentRevision", null=True, blank=True, on_delete=models.PROTECT, related_name="+")
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def clean(self) -> None:
        if self.status == DocumentStatus.EFFECTIVE:
            revision = self.current_revision
            if revision is None or revision.status != DocumentStatus.EFFECTIVE:
                raise ValidationError("Effective document requires an effective current revision.")

    def __str__(self) -> str:
        return self.document_number


class DocumentRevision(models.Model):
    document = models.ForeignKey(Document, on_delete=models.PROTECT, related_name="revisions")
    revision = models.PositiveIntegerField()
    source_file = models.FileField(upload_to="sources/%Y/%m/")
    source_filename = models.CharField(max_length=255)
    source_sha256 = models.CharField(max_length=64)
    official_pdf = models.FileField(upload_to="official_pdfs/%Y/%m/", blank=True)
    official_pdf_sha256 = models.CharField(max_length=64, blank=True)
    status = models.CharField(max_length=32, choices=DocumentStatus.choices, default=DocumentStatus.DRAFT_UPLOADED)
    effective_at = models.DateTimeField(null=True, blank=True)
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["document", "revision"], name="unique_revision_per_document")
        ]
