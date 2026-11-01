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
