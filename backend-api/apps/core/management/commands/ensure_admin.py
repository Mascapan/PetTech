"""
Crea (una sola vez) la cuenta de ADMINISTRADOR inicial a partir de variables de entorno.

Pensado para servidores sin consola interactiva (p. ej., plan gratuito de Render), donde no se
puede ejecutar `createsuperuser`. Se invoca en cada arranque y es idempotente:
- Si faltan las variables, no hace nada.
- Si la cuenta ya existe, NO cambia su contraseña (se gestiona luego desde el portal o
  con "¿Olvidaste tu contraseña?"), así una variable olvidada en el panel no la sobrescribe.

Variables:
  PETTECH_ADMIN_EMAIL      correo de acceso al portal
  PETTECH_ADMIN_PASSWORD   contraseña inicial (mínimo 12 caracteres; cámbiala tras el primer ingreso)
  PETTECH_ADMIN_NAME       nombre visible (opcional)
Nunca se escribe la contraseña en los registros.
"""
import os

from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError
from django.core.management.base import BaseCommand

from apps.accounts.models import User

MIN_LENGTH = 12


class Command(BaseCommand):
    help = "Crea la cuenta de administrador inicial desde variables de entorno (idempotente)."

    def handle(self, *args, **options):
        email = (os.environ.get("PETTECH_ADMIN_EMAIL") or "").strip().lower()
        password = os.environ.get("PETTECH_ADMIN_PASSWORD") or ""
        name = (os.environ.get("PETTECH_ADMIN_NAME") or "Administración PetTech").strip()

        if not email or not password:
            self.stdout.write("ensure_admin: PETTECH_ADMIN_EMAIL/PASSWORD no definidos; sin cambios.")
            return
        if User.objects.filter(email__iexact=email).exists():
            self.stdout.write("ensure_admin: la cuenta de administrador ya existe; sin cambios.")
            return
        if len(password) < MIN_LENGTH:
            self.stderr.write(f"ensure_admin: la contraseña debe tener al menos {MIN_LENGTH} caracteres; no se creó.")
            return
        try:
            validate_password(password, user=User(email=email, full_name=name))
        except ValidationError as exc:
            self.stderr.write("ensure_admin: contraseña rechazada: " + " ".join(exc.messages))
            return
        User.objects.create_superuser(email=email, password=password, full_name=name)
        self.stdout.write(self.style.SUCCESS("ensure_admin: cuenta de administrador creada."))
