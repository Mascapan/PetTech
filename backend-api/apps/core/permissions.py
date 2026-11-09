"""
Permisos por rol.

La autorización SIEMPRE se decide en el servidor: la aplicación Android solo adapta
la interfaz según el rol, pero no puede conceder ni ampliar permisos.
"""
from rest_framework.permissions import BasePermission

from apps.accounts.models import User


class IsTutor(BasePermission):
    """Permite el acceso solo a cuentas activas con rol TUTOR (propietario de mascotas)."""

    message = "Esta función está disponible solo para cuentas de tutor."

    def has_permission(self, request, view) -> bool:
        user = request.user
        return bool(user and user.is_authenticated and user.is_active and user.role == User.Role.TUTOR)


class IsProfessional(BasePermission):
    """Cuentas activas con rol PROFESSIONAL. La verificación del vínculo con un establecimiento
    y la autorización del tutor se comprueban además en apps.access_control.services."""

    message = "Esta función está disponible solo para profesionales veterinarios."

    def has_permission(self, request, view) -> bool:
        user = request.user
        return bool(user and user.is_authenticated and user.is_active and user.role == User.Role.PROFESSIONAL)


class IsTutorOrProfessional(BasePermission):
    message = "Esta función está disponible solo para tutores y profesionales."

    def has_permission(self, request, view) -> bool:
        user = request.user
        return bool(
            user and user.is_authenticated and user.is_active
            and user.role in (User.Role.TUTOR, User.Role.PROFESSIONAL)
        )
