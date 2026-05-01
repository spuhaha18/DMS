from django.contrib.auth.decorators import login_required
from django.shortcuts import render

from .models import AuditEvent


@login_required
def event_list(request):
    events = AuditEvent.objects.select_related("actor").order_by("-created_at")

    document_number = request.GET.get("document_number", "").strip()
    username = request.GET.get("username", "").strip()
    event_type = request.GET.get("event_type", "").strip()
    date_from = request.GET.get("date_from", "").strip()
    date_to = request.GET.get("date_to", "").strip()

    if document_number:
        events = events.filter(after__icontains=document_number) | events.filter(object_id=document_number)
    if username:
        events = events.filter(actor__username__icontains=username)
    if event_type:
        events = events.filter(event_type__icontains=event_type)
    if date_from:
        events = events.filter(created_at__date__gte=date_from)
    if date_to:
        events = events.filter(created_at__date__lte=date_to)

    events = events[:500]
    return render(request, "audit/event_list.html", {
        "events": events,
        "document_number": document_number,
        "username": username,
        "event_type": event_type,
        "date_from": date_from,
        "date_to": date_to,
    })
