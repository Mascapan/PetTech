from django.contrib import admin

from .models import ClinicalEntry


@admin.register(ClinicalEntry)
class ClinicalEntryAdmin(admin.ModelAdmin):
    """Solo lectura: el administrador no edita información clínica (RN-11, DEC-016)."""

    list_display = ("pet", "entry_type", "title", "occurred_on", "origin", "clinic_name", "author_name")
    list_filter = ("entry_type", "origin")
    search_fields = ("pet__public_code", "title")

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False
