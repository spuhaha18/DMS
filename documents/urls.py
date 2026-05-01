from django.urls import path

from . import views

app_name = "documents"

urlpatterns = [
    path("", views.register, name="register"),
    path("new/", views.register_document_view, name="register_document"),
    path("<int:pk>/", views.detail, name="detail"),
    path("<int:pk>/evidence/", views.evidence_package, name="evidence_package"),
    path("<int:pk>/submit/", views.submit_for_approval_view, name="submit_for_approval"),
    path("<int:pk>/generate-pdf/", views.generate_pdf_view, name="generate_pdf"),
]
