from django.urls import path

from . import views

app_name = "documents"

urlpatterns = [
    path("", views.register, name="register"),
    path("new/", views.register_document_view, name="register_document"),
    path("<int:pk>/", views.detail, name="detail"),
    path("<int:pk>/evidence/", views.evidence_package, name="evidence_package"),
]
