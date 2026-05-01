from django.core.exceptions import ValidationError
from django.db import transaction
from django.utils import timezone

from accounts.services import require_password_reentry
from approvals.models import ApprovalTask, ApprovalTaskStatus, ElectronicSignature
from audit.services import append_event
from documents.models import DocumentStatus


@transaction.atomic
def submit_for_approval(revision, *, actor, reason: str):
    if revision.status == DocumentStatus.IN_REVIEW:
        raise ValidationError("Revision is already submitted for approval.")
    if revision.status not in {DocumentStatus.DRAFT_UPLOADED, DocumentStatus.REJECTED}:
        raise ValidationError(f"Cannot submit revision with status '{revision.status}' for approval.")

    # Remove stale tasks from previous rejected cycles so the completion check works correctly.
    # CANCELLED and REJECTED tasks have no ElectronicSignature (PROTECT), so deletion is safe.
    revision.approval_tasks.filter(
        status__in=[ApprovalTaskStatus.CANCELLED, ApprovalTaskStatus.REJECTED]
    ).delete()

    from approvals.models import ApprovalRouteTemplate

    doc_type = revision.document.document_type
    template = ApprovalRouteTemplate.objects.filter(document_type=doc_type, is_active=True).first()
    if template is None:
        raise ValidationError("No active approval route template found for this document type.")

    steps = list(template.steps.all())
    if not steps:
        raise ValidationError("Approval route template has no steps.")

    for step in steps:
        assignees = list(step.role.user_set.filter(is_active=True))
        if not assignees:
            raise ValidationError(f"No active users in group '{step.role.name}' for step {step.order}.")
        ApprovalTask.objects.create(
            revision=revision,
            order=step.order,
            assigned_to=assignees[0],
            role_name=step.role.name,
            signature_meaning=step.signature_meaning,
            route_template_name=template.name,
        )

    revision.status = DocumentStatus.IN_REVIEW
    revision.save(update_fields=["status"])
    revision.document.status = DocumentStatus.IN_REVIEW
    revision.document.save(update_fields=["status", "updated_at"])

    append_event(
        actor=actor,
        event_type="document.submitted_for_approval",
        object_type="DocumentRevision",
        object_id=str(revision.id),
        after={"status": revision.status},
        reason=reason,
    )


@transaction.atomic
def approve_task(task, *, signer, password: str, comment: str, reason: str):
    if task.assigned_to != signer:
        raise ValidationError("You are not assigned to this task.")
    if task.status != ApprovalTaskStatus.PENDING:
        raise ValidationError(f"Task is not pending (current status: {task.status}).")

    prior_tasks = ApprovalTask.objects.filter(revision=task.revision, order__lt=task.order)
    if prior_tasks.exclude(status=ApprovalTaskStatus.APPROVED).exists():
        raise ValidationError("Prior approval tasks must be approved first.")

    require_password_reentry(signer, password)

    task.status = ApprovalTaskStatus.APPROVED
    task.comment = comment
    task.completed_at = timezone.now()
    task.save(update_fields=["status", "comment", "completed_at"])

    ElectronicSignature.objects.create(
        task=task,
        signer=signer,
        meaning=task.signature_meaning,
        source_sha256=task.revision.source_sha256,
    )

    append_event(
        actor=signer,
        event_type="approval.task_approved",
        object_type="ApprovalTask",
        object_id=str(task.id),
        after={"status": task.status, "signature_meaning": task.signature_meaning},
        reason=reason,
    )

    all_done = not ApprovalTask.objects.filter(revision=task.revision).exclude(status=ApprovalTaskStatus.APPROVED).exists()
    if all_done:
        revision = task.revision
        revision.status = DocumentStatus.APPROVED
        revision.save(update_fields=["status"])
        revision.document.status = DocumentStatus.APPROVED
        revision.document.save(update_fields=["status", "updated_at"])
        append_event(
            actor=signer,
            event_type="document.approved",
            object_type="DocumentRevision",
            object_id=str(revision.id),
            after={"status": revision.status},
            reason=reason,
        )


@transaction.atomic
def reject_task(task, *, signer, password: str, comment: str, reason: str):
    if task.assigned_to != signer:
        raise ValidationError("You are not assigned to this task.")
    if task.status != ApprovalTaskStatus.PENDING:
        raise ValidationError(f"Task is not pending (current status: {task.status}).")

    require_password_reentry(signer, password)

    task.status = ApprovalTaskStatus.REJECTED
    task.comment = comment
    task.completed_at = timezone.now()
    task.save(update_fields=["status", "comment", "completed_at"])

    ApprovalTask.objects.filter(revision=task.revision, status=ApprovalTaskStatus.PENDING).exclude(pk=task.pk).update(status=ApprovalTaskStatus.CANCELLED)

    revision = task.revision
    revision.status = DocumentStatus.REJECTED
    revision.save(update_fields=["status"])
    revision.document.status = DocumentStatus.REJECTED
    revision.document.save(update_fields=["status", "updated_at"])

    append_event(
        actor=signer,
        event_type="approval.task_rejected",
        object_type="ApprovalTask",
        object_id=str(task.id),
        after={"status": task.status},
        reason=reason,
    )
