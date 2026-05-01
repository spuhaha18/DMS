from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand

from documents.models import DocumentRevision
from pdfs.services import generate_official_pdf


class Command(BaseCommand):
    help = "Generate official PDF for an approved document revision."

    def add_arguments(self, parser):
        parser.add_argument("revision_id", type=int)
        parser.add_argument("--actor", required=True)

    def handle(self, *args, **options):
        revision = DocumentRevision.objects.get(pk=options["revision_id"])
        actor = get_user_model().objects.get(username=options["actor"])
        generate_official_pdf(revision, actor=actor, reason="management command")
        self.stdout.write(self.style.SUCCESS(f"Generated official PDF for revision {revision.id}"))
