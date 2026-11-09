from django.urls import path

from .views import AccessRequestListCreateView, ApproveView, PetAuditLogView, RejectView, RevokeView

urlpatterns = [
    path("requests/", AccessRequestListCreateView.as_view(), name="access-requests"),
    path("requests/<uuid:pk>/approve/", ApproveView.as_view(), name="access-approve"),
    path("requests/<uuid:pk>/reject/", RejectView.as_view(), name="access-reject"),
    path("requests/<uuid:pk>/revoke/", RevokeView.as_view(), name="access-revoke"),
    path("pets/<uuid:pet_id>/log/", PetAuditLogView.as_view(), name="access-pet-log"),
]
