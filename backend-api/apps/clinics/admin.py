from django.contrib import admin
from django.contrib.gis.admin import GISModelAdmin
from django.utils import timezone

from .models import Clinic, OpeningHour, ProfessionalMembership


class OpeningHourInline(admin.TabularInline):
    model = OpeningHour
    extra = 0


class MembershipInline(admin.TabularInline):
    model = ProfessionalMembership
    extra = 0
    fields = ("professional", "license_number", "is_verified", "verified_at")


@admin.register(Clinic)
class ClinicAdmin(GISModelAdmin):
    """
    Catálogo de establecimientos (HU-11, HU-12). La ubicación se define sobre un mapa.
    Cada cambio queda en la bitácora de Django Admin (autor, fecha, objeto y campos modificados).
    """

    list_display = ("name", "commune", "attends_emergencies", "emergency_24h", "info_origin",
                    "verified_at", "accepts_pettech_records", "is_active")
    list_filter = ("info_origin", "attends_emergencies", "emergency_24h", "accepts_pettech_records", "commune")
    search_fields = ("name", "address")
    inlines = [OpeningHourInline, MembershipInline]
    actions = ["mark_verified_today"]

    @admin.action(description="Marcar como verificados hoy")
    def mark_verified_today(self, request, queryset):
        queryset.update(verified_at=timezone.now())


@admin.register(ProfessionalMembership)
class MembershipAdmin(admin.ModelAdmin):
    list_display = ("professional", "clinic", "is_verified", "verified_at")
    list_filter = ("is_verified", "clinic")
    search_fields = ("professional__email", "clinic__name")

    def save_model(self, request, obj, form, change):
        if obj.is_verified and obj.verified_at is None:
            obj.verified_at = timezone.now()
        super().save_model(request, obj, form, change)
