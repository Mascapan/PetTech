"""Rutas raíz de PetTech. Toda la API está versionada bajo /api/v1/."""
from django.conf import settings
from django.contrib import admin
from django.urls import include, path
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView

admin.site.site_header = "PetTech — Administración"
admin.site.site_title = "PetTech Admin"
admin.site.index_title = "Gestión interna (uso exclusivo del equipo PetTech)"

api_v1 = [
    path("", include("apps.core.urls")),
    path("auth/", include("apps.accounts.urls")),
    path("pets/", include("apps.pets.urls")),
    path("schema/", SpectacularAPIView.as_view(), name="schema"),
    path("docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="swagger-ui"),
]

urlpatterns = [
    path(settings.ADMIN_URL, admin.site.urls),
    path("api/v1/", include(api_v1)),
]

# Respuestas JSON uniformes para rutas inexistentes y errores no controlados.
handler404 = "apps.core.views.json_not_found"
handler500 = "apps.core.views.json_server_error"
