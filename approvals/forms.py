from django import forms
from django.utils.translation import gettext_lazy as _


class ApprovalDecisionForm(forms.Form):
    DECISION_CHOICES = [("approve", _("승인")), ("reject", _("반려"))]
    decision = forms.ChoiceField(
        label=_("결정"),
        choices=DECISION_CHOICES,
        widget=forms.RadioSelect,
    )
    password = forms.CharField(
        label=_("비밀번호 재입력"),
        widget=forms.PasswordInput,
    )
    comment = forms.CharField(
        label=_("의견"),
        max_length=500, required=False,
        widget=forms.Textarea(attrs={"rows": 3}),
    )
    reason = forms.CharField(
        label=_("결재 사유"),
        max_length=500,
        widget=forms.Textarea(attrs={"rows": 2}),
    )
