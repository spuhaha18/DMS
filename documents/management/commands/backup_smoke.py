import hashlib

from django.core.management.base import BaseCommand, CommandError

from audit.services import validate_hash_chain
from audit.models import AuditEvent
from approvals.models import ElectronicSignature
from documents.models import Document, DocumentRevision
from viewer.models import ViewEvent


class Command(BaseCommand):
    help = "Smoke-test backup integrity: verify file hashes and audit chain."

    def handle(self, *args, **options):
        errors = []

        for revision in DocumentRevision.objects.all():
            if revision.source_file and revision.source_sha256:
                try:
                    hasher = hashlib.sha256()
                    with revision.source_file.open("rb") as f:
                        for chunk in iter(lambda: f.read(8192), b""):
                            hasher.update(chunk)
                    computed = hasher.hexdigest()
                    if computed != revision.source_sha256:
                        errors.append(f"Revision {revision.id} source_sha256 mismatch: stored={revision.source_sha256} computed={computed}")
                except Exception as exc:
                    errors.append(f"Revision {revision.id} source file read error: {exc}")

            if revision.official_pdf and revision.official_pdf_sha256:
                try:
                    hasher = hashlib.sha256()
                    with revision.official_pdf.open("rb") as f:
                        for chunk in iter(lambda: f.read(8192), b""):
                            hasher.update(chunk)
                    computed = hasher.hexdigest()
                    if computed != revision.official_pdf_sha256:
                        errors.append(f"Revision {revision.id} official_pdf_sha256 mismatch: stored={revision.official_pdf_sha256} computed={computed}")
                except Exception as exc:
                    errors.append(f"Revision {revision.id} official PDF read error: {exc}")

        chain_errors = validate_hash_chain()
        errors.extend(chain_errors)

        doc_count = Document.objects.count()
        rev_count = DocumentRevision.objects.count()
        sig_count = ElectronicSignature.objects.count()
        view_count = ViewEvent.objects.count()
        audit_count = AuditEvent.objects.count()

        self.stdout.write(f"Documents: {doc_count} | Revisions: {rev_count} | Signatures: {sig_count} | Views: {view_count} | Audit events: {audit_count}")

        if errors:
            for err in errors:
                self.stderr.write(self.style.ERROR(err))
            raise CommandError("Backup smoke FAILED — see errors above.")

        self.stdout.write(self.style.SUCCESS("Backup smoke passed"))
