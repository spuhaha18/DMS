from django.urls import path

from . import views

app_name = "approvals"

urlpatterns = [
    path("", views.task_list, name="task_list"),
    path("<int:pk>/", views.task_detail, name="task_detail"),
]
