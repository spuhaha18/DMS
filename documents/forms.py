from django import forms
from django.core.exceptions import ValidationError

from .models import DocumentType, ProjectCode
from .services import _validate_doc_extension


class DocumentRegistrationForm(forms.Form):
    project_code = forms.ModelChoiceField(queryset=ProjectCode.objects.filter(is_active=True))
    document_type = forms.ModelChoiceField(queryset=DocumentType.objects.filter(is_active=True))
    title = forms.CharField(max_length=255)
    source_file = forms.FileField()
    reason = forms.CharField(max_length=500, widget=forms.Textarea(attrs={"rows": 3}))

    def clean_source_file(self):
        f = self.cleaned_data["source_file"]
        try:
            _validate_doc_extension(f.name)
        except ValidationError as e:
            raise forms.ValidationError(str(e))
        return f
