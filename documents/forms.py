from django import forms
from django.core.exceptions import ValidationError
from django.utils.translation import gettext_lazy as _

from .models import DocumentType, ProjectCode
from .services import _validate_doc_extension


class DocumentRegistrationForm(forms.Form):
    project_code = forms.ModelChoiceField(
        label=_("프로젝트 코드"),
        queryset=ProjectCode.objects.filter(is_active=True),
    )
    document_type = forms.ModelChoiceField(
        label=_("문서 유형"),
        queryset=DocumentType.objects.filter(is_active=True),
    )
    title = forms.CharField(label=_("제목"), max_length=255)
    source_file = forms.FileField(label=_("원본 파일"))
    reason = forms.CharField(
        label=_("등록 사유"), max_length=500,
        widget=forms.Textarea(attrs={"rows": 3}),
    )

    def clean_source_file(self):
        f = self.cleaned_data["source_file"]
        try:
            _validate_doc_extension(f.name)
        except ValidationError as e:
            raise forms.ValidationError(str(e))
        return f
