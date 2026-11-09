from django.urls import path

from . import views

app_name = "portal"

urlpatterns = [
    path("", views.HomeView.as_view(), name="home"),
    path("ingresar/", views.LoginView.as_view(), name="login"),
    path("salir/", views.LogoutView.as_view(), name="logout"),
    # Administrador de PetTech
    path("admin/", views.AdminDashboardView.as_view(), name="admin-dashboard"),
    path("admin/veterinarias/", views.AdminClinicListView.as_view(), name="admin-clinics"),
    path("admin/veterinarias/nueva/", views.AdminClinicCreateView.as_view(), name="admin-clinic-new"),
    path("admin/veterinarias/<uuid:pk>/", views.AdminClinicDetailView.as_view(), name="admin-clinic"),
    # Veterinaria (establecimiento)
    path("veterinaria/", views.ClinicDashboardView.as_view(), name="clinic-dashboard"),
    path("veterinaria/profesionales/nuevo/", views.ClinicProfessionalCreateView.as_view(), name="clinic-pro-new"),
    path("veterinaria/profesionales/<int:pk>/autorizacion/", views.ClinicProfessionalToggleView.as_view(),
         name="clinic-pro-toggle"),
    path("veterinaria/establecimiento/", views.ClinicProfileView.as_view(), name="clinic-profile"),
    # Profesional veterinario
    path("profesional/", views.VetHomeView.as_view(), name="vet-home"),
    path("profesional/ficha/<str:code>/", views.VetRecordView.as_view(), name="vet-record"),
    path("profesional/ficha/<str:code>/corregir/<uuid:entry_id>/", views.VetCorrectView.as_view(), name="vet-correct"),
]
