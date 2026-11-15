from django.contrib.auth.mixins import LoginRequiredMixin
from django.shortcuts import render

from apps.accounts.models import User

PORTAL_ROLES = {User.Role.ADMIN, User.Role.CLINIC, User.Role.PROFESSIONAL}


class RoleRequiredMixin(LoginRequiredMixin):
    """Denegar por defecto: exige sesión y uno de los roles permitidos (403 si el rol no corresponde)."""

    allowed_roles: tuple = ()

    def dispatch(self, request, *args, **kwargs):
        if not request.user.is_authenticated:
            return self.handle_no_permission()
        if not request.user.is_active or request.user.role not in self.allowed_roles:
            return render(request, "portal/403.html", status=403)
        return super().dispatch(request, *args, **kwargs)


class AdminRequiredMixin(RoleRequiredMixin):
    allowed_roles = (User.Role.ADMIN,)


class ProfessionalRequiredMixin(RoleRequiredMixin):
    allowed_roles = (User.Role.PROFESSIONAL,)


class ClinicRequiredMixin(LoginRequiredMixin):
    """Cuenta de veterinaria con establecimiento asociado y habilitado por PetTech."""

    def dispatch(self, request, *args, **kwargs):
        if not request.user.is_authenticated:
            return self.handle_no_permission()
        if request.user.role != User.Role.CLINIC or not request.user.is_active:
            return render(request, "portal/403.html", status=403)
        clinic = getattr(request.user, "managed_clinic", None)
        if clinic is None or not clinic.is_active:
            return render(request, "portal/403.html", {
                "message": "Tu cuenta no tiene un establecimiento habilitado. Contacta al administrador de PetTech."
            }, status=403)
        self.clinic = clinic
        return super().dispatch(request, *args, **kwargs)


class PortalUserRequiredMixin(RoleRequiredMixin):
    """Cualquier rol del portal (p. ej. para cambiar la propia contraseña)."""

    allowed_roles = tuple(PORTAL_ROLES)
