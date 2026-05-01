from django import forms


class ApprovalDecisionForm(forms.Form):
    DECISION_CHOICES = [("approve", "Approve"), ("reject", "Reject")]
    decision = forms.ChoiceField(choices=DECISION_CHOICES, widget=forms.RadioSelect)
    password = forms.CharField(widget=forms.PasswordInput)
    comment = forms.CharField(max_length=500, required=False, widget=forms.Textarea(attrs={"rows": 3}))
    reason = forms.CharField(max_length=500, widget=forms.Textarea(attrs={"rows": 2}))
