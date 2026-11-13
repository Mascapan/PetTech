"""Creación del administrador inicial sin consola (despliegue en Render)."""
import os
from io import StringIO
from unittest import mock

from django.core.management import call_command
from django.test import TestCase

from apps.accounts.models import User


class EnsureAdminTests(TestCase):
    def run_cmd(self, **env):
        out = StringIO()
        with mock.patch.dict(os.environ, env, clear=False):
            call_command("ensure_admin", stdout=out, stderr=out)
        return out.getvalue()

    def test_sin_variables_no_hace_nada(self):
        with mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop("PETTECH_ADMIN_EMAIL", None)
            os.environ.pop("PETTECH_ADMIN_PASSWORD", None)
            call_command("ensure_admin", stdout=StringIO())
        self.assertFalse(User.objects.filter(role=User.Role.ADMIN).exists())

    def test_crea_admin_una_sola_vez_y_no_cambia_la_clave(self):
        self.run_cmd(PETTECH_ADMIN_EMAIL="Admin@PetTech.test", PETTECH_ADMIN_PASSWORD="Clave-Inicial-Segura-1")
        admin = User.objects.get(email="admin@pettech.test")
        self.assertEqual(admin.role, User.Role.ADMIN)
        self.assertTrue(admin.is_superuser)
        out = self.run_cmd(PETTECH_ADMIN_EMAIL="admin@pettech.test", PETTECH_ADMIN_PASSWORD="Otra-Clave-Distinta-2")
        self.assertIn("ya existe", out)
        admin.refresh_from_db()
        self.assertTrue(admin.check_password("Clave-Inicial-Segura-1"))

    def test_rechaza_clave_corta_o_debil_sin_mostrarla(self):
        out = self.run_cmd(PETTECH_ADMIN_EMAIL="a@pettech.test", PETTECH_ADMIN_PASSWORD="corta")
        self.assertFalse(User.objects.exists())
        self.assertNotIn("corta", out.replace("contraseña", ""))
        self.run_cmd(PETTECH_ADMIN_EMAIL="a@pettech.test", PETTECH_ADMIN_PASSWORD="123456789012")
        self.assertFalse(User.objects.exists())
