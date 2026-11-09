"""Utilidades compartidas por las pruebas automatizadas (datos siempre ficticios)."""
from django.core.cache import cache
from rest_framework.test import APITestCase

from apps.accounts.models import User
from apps.accounts.services import issue_token

DEFAULT_PASSWORD = "Clave-Segura-2026"


class PetTechAPITestCase(APITestCase):
    """Limpia la caché (límites de tasa) antes de cada prueba para evitar interferencias."""

    def setUp(self):
        super().setUp()
        cache.clear()

    @staticmethod
    def make_user(email="tutor@pettech.test", role=User.Role.TUTOR, password=DEFAULT_PASSWORD, **extra):
        return User.objects.create_user(
            email=email, password=password, full_name=extra.pop("full_name", "Persona de Prueba"), role=role, **extra
        )

    def authenticate(self, user):
        _, token = issue_token(user)
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {token}")
        return token

    def logout_client(self):
        self.client.credentials()

    def assertErrorCode(self, response, status_code, code):
        self.assertEqual(response.status_code, status_code, response.content)
        self.assertIn("error", response.json())
        self.assertEqual(response.json()["error"]["code"], code)


def make_clinic(name="Clínica Prueba", lat=-36.6066, lng=-72.1034, *, network=True, **extra):
    """Establecimiento ficticio para pruebas (por defecto, en la red PetTech)."""
    from django.contrib.gis.geos import Point
    from django.utils import timezone

    from apps.clinics.models import Clinic

    return Clinic.objects.create(
        name=name, address="Dirección ficticia", location=Point(lng, lat, srid=4326),
        verified_at=extra.pop("verified_at", timezone.now()), accepts_pettech_records=network, **extra
    )


def link_professional(user, clinic, verified=True):
    from apps.clinics.models import ProfessionalMembership

    return ProfessionalMembership.objects.create(professional=user, clinic=clinic, is_verified=verified)
