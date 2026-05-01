from django.urls import path

from . import views

app_name = "viewer"

urlpatterns = [
    path("document/<int:document_pk>/", views.pdf_view, name="pdf_view"),
    path("document/<int:document_pk>/revision/<int:revision_id>/", views.pdf_view, name="pdf_view_revision"),
    path("document/<int:document_pk>/pdf/", views.pdf_stream, name="pdf_stream"),
    path("document/<int:document_pk>/revision/<int:revision_id>/pdf/", views.pdf_stream, name="pdf_stream_revision"),
]
