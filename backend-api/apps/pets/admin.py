from django.contrib import admin

from .models import Pet


@admin.register(Pet)
class PetAdmin(admin.ModelAdmin):
    """
    Vista de SOLO LECTURA para supervisión.

    Principio de mínimo privilegio: el administrador no edita datos de mascotas ni
    antecedentes; esos cambios corresponden al tutor (o al profesional autorizado en Fase 3).
    """

    list_display = ("name", "public_code", "species", "owner", "created_at")
    list_filter = ("species", "sex")
    search_fields = ("public_code", "name", "owner__email")
    readonly_fields = [f.name for f in Pet._meta.fields]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False
