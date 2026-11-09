from django.contrib import admin

from .models import AccessRequest, AuditEvent


class ReadOnlyAdmin(admin.ModelAdmin):
    """Supervisión de solo lectura: el administrador no concede accesos clínicos (RN-11)."""

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(AccessRequest)
class AccessRequestAdmin(ReadOnlyAdmin):
    list_display = ("pet", "clinic", "professional", "status", "created_at", "expires_at")
    list_filter = ("status", "clinic")


@admin.register(AuditEvent)
class AuditEventAdmin(ReadOnlyAdmin):
    list_display = ("created_at", "actor_name", "clinic_name", "pet_code", "action", "result")
    list_filter = ("action", "result")
    search_fields = ("pet_code", "actor_name", "clinic_name")
