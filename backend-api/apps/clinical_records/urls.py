from django.urls import path

from . import views

# Rutas del tutor (montadas bajo /api/v1/pets/<pet_id>/clinical-record/).
owner_urlpatterns = [
    path("", views.OwnerRecordView.as_view(), name="owner-record"),
    path("entries/", views.OwnerEntryCreateView.as_view(), name="owner-entry-create"),
    path("entries/<uuid:entry_id>/correct/", views.OwnerEntryCorrectView.as_view(), name="owner-entry-correct"),
    path("entries/<uuid:entry_id>/history/", views.OwnerEntryHistoryView.as_view(), name="owner-entry-history"),
    path("attachments/", views.OwnerAttachmentCreateView.as_view(), name="owner-attachment-create"),
    path("attachments/<uuid:attachment_id>/", views.OwnerAttachmentView.as_view(), name="owner-attachment"),
]

# Rutas del profesional (montadas bajo /api/v1/professional/).
professional_urlpatterns = [
    path("me/", views.ProfessionalProfileView.as_view(), name="pro-me"),
    path("pets/<str:code>/clinical-record/", views.ProfessionalRecordView.as_view(), name="pro-record"),
    path("pets/<str:code>/clinical-record/entries/", views.ProfessionalEntryCreateView.as_view(),
         name="pro-entry-create"),
    path("pets/<str:code>/clinical-record/entries/<uuid:entry_id>/correct/",
         views.ProfessionalEntryCorrectView.as_view(), name="pro-entry-correct"),
    path("pets/<str:code>/clinical-record/attachments/", views.ProfessionalAttachmentCreateView.as_view(),
         name="pro-attachment-create"),
    path("pets/<str:code>/clinical-record/attachments/<uuid:attachment_id>/",
         views.ProfessionalAttachmentView.as_view(), name="pro-attachment"),
]
