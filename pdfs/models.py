from django.db import models


class PdfConversionStatus(models.TextChoices):
    PENDING = "Pending", "Pending"
    SUCCEEDED = "Succeeded", "Succeeded"
    FAILED = "Failed", "Failed"


class PdfConversionJob(models.Model):
    revision = models.OneToOneField("documents.DocumentRevision", on_delete=models.PROTECT, related_name="pdf_job")
    status = models.CharField(max_length=24, choices=PdfConversionStatus.choices, default=PdfConversionStatus.PENDING)
    converter_version = models.CharField(max_length=255, blank=True)
    error_message = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)


class QaException(models.Model):
    revision = models.ForeignKey("documents.DocumentRevision", on_delete=models.PROTECT, related_name="qa_exceptions")
    exception_type = models.CharField(max_length=120)
    message = models.TextField()
    is_resolved = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
