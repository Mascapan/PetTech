"""Pruebas de infraestructura: PostgreSQL/PostGIS, salud del servicio y formato de errores."""
from django.db import connection
from django.test import TestCase
from django.urls import reverse

from .helpers import PetTechAPITestCase


class DatabaseTests(TestCase):
    def test_motor_es_postgresql(self):
        self.assertEqual(connection.vendor, "postgresql")

    def test_extension_postgis_disponible(self):
        with connection.cursor() as cursor:
            cursor.execute("SELECT extname FROM pg_extension WHERE extname = 'postgis'")
            self.assertIsNotNone(cursor.fetchone(), "La extensión PostGIS no está instalada en la base de pruebas.")

    def test_postgis_resuelve_consulta_espacial(self):
        # Distancia geodésica aproximada entre dos puntos ficticios de Chillán (en metros).
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT ST_Distance("
                "ST_SetSRID(ST_MakePoint(-72.1034, -36.6066), 4326)::geography,"
                "ST_SetSRID(ST_MakePoint(-72.0934, -36.6066), 4326)::geography)"
            )
            distance = cursor.fetchone()[0]
        self.assertGreater(distance, 800)
        self.assertLess(distance, 1000)


class HealthAndErrorFormatTests(PetTechAPITestCase):
    def test_health_publico_y_sin_detalles_internos(self):
        response = self.client.get(reverse("health"))
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok", "database": "ok", "postgis": "ok"})

    def test_error_401_tiene_formato_uniforme(self):
        response = self.client.get(reverse("auth-me"))
        self.assertErrorCode(response, 401, "not_authenticated")
        self.assertIn("message", response.json()["error"])

    def test_error_validacion_incluye_campos(self):
        response = self.client.post(reverse("auth-login"), {}, format="json")
        self.assertErrorCode(response, 400, "validation_error")
        self.assertIn("email", response.json()["error"]["fields"])
        self.assertIn("password", response.json()["error"]["fields"])

    def test_json_malformado_responde_400_sin_traza(self):
        response = self.client.generic(
            "POST", reverse("auth-login"), data="{no es json", content_type="application/json"
        )
        self.assertErrorCode(response, 400, "parse_error")
        self.assertNotIn("Traceback", response.content.decode())
