from django.urls import path

from .views import ClinicDetailView, ClinicSearchView, ServiceListView

urlpatterns = [
    path("", ClinicSearchView.as_view(), name="clinic-search"),
    path("services/", ServiceListView.as_view(), name="clinic-services"),
    path("<uuid:pk>/", ClinicDetailView.as_view(), name="clinic-detail"),
]
