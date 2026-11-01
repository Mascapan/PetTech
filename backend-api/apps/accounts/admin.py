from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as BaseUserAdmin
from django.contrib.auth.forms import BaseUserCreationForm, UserChangeForm

from .models import User


class PetTechUserCreationForm(BaseUserCreationForm):
    class Meta(BaseUserCreationForm.Meta):
        model = User
        fields = ("email", "full_name", "role")


class PetTechUserChangeForm(UserChangeForm):
    class Meta(UserChangeForm.Meta):
        model = User
        fields = "__all__"


@admin.register(User)
class UserAdmin(BaseUserAdmin):
    """
    Gestión de usuarios desde Django Admin.

    El administrador puede suspender cuentas (is_active) y crear cuentas profesionales;
    la verificación profesional y su asociación a establecimientos se agregan en la Fase 2.
    """

    form = PetTechUserChangeForm
    add_form = PetTechUserCreationForm
    model = User

    list_display = ("email", "full_name", "role", "is_active", "is_staff", "date_joined")
    list_filter = ("role", "is_active", "is_staff")
    search_fields = ("email", "full_name")
    ordering = ("-date_joined",)
    readonly_fields = ("id", "date_joined", "last_login", "privacy_policy_version", "privacy_policy_accepted_at")

    fieldsets = (
        (None, {"fields": ("id", "email", "password")}),
        ("Perfil", {"fields": ("full_name", "role")}),
        ("Consentimiento", {"fields": ("privacy_policy_version", "privacy_policy_accepted_at")}),
        ("Estado y permisos", {"fields": ("is_active", "is_staff", "is_superuser", "groups", "user_permissions")}),
        ("Fechas", {"fields": ("last_login", "date_joined")}),
    )
    add_fieldsets = (
        (
            None,
            {
                "classes": ("wide",),
                "fields": ("email", "full_name", "role", "password1", "password2"),
            },
        ),
    )
