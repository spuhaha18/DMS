from django.contrib.auth.decorators import login_required
from django.shortcuts import get_object_or_404, redirect, render

from approvals.forms import ApprovalDecisionForm
from approvals.models import ApprovalTask, ApprovalTaskStatus
from approvals.services import approve_task, reject_task


@login_required
def task_list(request):
    tasks = ApprovalTask.objects.filter(assigned_to=request.user, status=ApprovalTaskStatus.PENDING).select_related("revision__document")
    return render(request, "approvals/task_list.html", {"tasks": tasks})


@login_required
def task_detail(request, pk):
    task = get_object_or_404(ApprovalTask.objects.select_related("revision__document", "assigned_to"), pk=pk)
    form = ApprovalDecisionForm(request.POST or None)
    error = None
    if request.method == "POST" and form.is_valid():
        data = form.cleaned_data
        try:
            if data["decision"] == "approve":
                approve_task(task, signer=request.user, password=data["password"], comment=data.get("comment", ""), reason=data["reason"])
            else:
                reject_task(task, signer=request.user, password=data["password"], comment=data.get("comment", ""), reason=data["reason"])
            return redirect("approvals:task_list")
        except Exception as e:
            error = str(e)
    return render(request, "approvals/task_detail.html", {"task": task, "form": form, "error": error})
