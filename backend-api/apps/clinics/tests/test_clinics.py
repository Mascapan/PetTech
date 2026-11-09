"""Búsqueda de establecimientos (HU-07, HU-08, HU-09) y cálculo de horario declarado."""
from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo

from django.test import TestCase, override_settings
from django.utils import timezone

from apps.clinics import services
from apps.clinics.models import OpeningHour
from apps.core.tests.helpers import PetTechAPITestCase, make_clinic

TZ = ZoneInfo("America/Santiago")


class OpeningHoursTests(TestCase):
    def setUp(self):
        self.clinic = make_clinic()

    def at(self, weekday, hh, mm=0):
        # 2026-11-09 es lunes (weekday 0).
        return datetime(2026, 11, 9 + weekday, hh, mm, tzinfo=TZ)

    def test_abierto_dentro_de_la_franja_declarada(self):
        OpeningHour.objects.create(clinic=self.clinic, weekday=0, opens_at=time(9), closes_at=time(18))
        self.assertTrue(services.is_open_at(self.clinic, self.at(0, 10)))
        self.assertFalse(services.is_open_at(self.clinic, self.at(0, 18)))
        self.assertFalse(services.is_open_at(self.clinic, self.at(1, 10)))

    def test_franja_nocturna_que_cruza_la_medianoche(self):
        OpeningHour.objects.create(clinic=self.clinic, weekday=0, opens_at=time(20), closes_at=time(8))
        self.assertTrue(services.is_open_at(self.clinic, self.at(0, 23)))
        self.assertTrue(services.is_open_at(self.clinic, self.at(1, 7, 59)))
        self.assertFalse(services.is_open_at(self.clinic, self.at(1, 8)))

    def test_atencion_24_horas_siempre_abierta(self):
        self.clinic.emergency_24h = True
        self.assertTrue(services.is_open_at(self.clinic, self.at(6, 3)))

    @override_settings(PETTECH_CLINIC_STALE_DAYS=30)
    def test_informacion_antigua_se_marca(self):
        self.clinic.verified_at = timezone.now() - timedelta(days=31)
        self.assertTrue(services.is_stale(self.clinic))


class ClinicSearchApiTests(PetTechAPITestCase):
    URL = "/api/v1/clinics/"

    def setUp(self):
        super().setUp()
        self.authenticate(self.make_user())
        self.near = make_clinic("Cerca", -36.6070, -72.1030, attends_emergencies=True, services=["GENERAL"])
        self.far = make_clinic("Lejos", -36.6400, -72.1300, emergency_24h=True, attends_emergencies=True,
                               services=["SURGERY"])
        self.out = make_clinic("Fuera de radio", -36.4245, -71.9580)

    def test_requiere_autenticacion(self):
        self.logout_client()
        self.assertEqual(self.client.get(self.URL).status_code, 401)

    def test_busqueda_por_cercania_respeta_el_radio_e_informa_distancia(self):
        data = self.client.get(self.URL, {"lat": -36.6066, "lng": -72.1034, "radius_km": 10}).json()
        names = [c["name"] for c in data["results"]]
        self.assertNotIn("Fuera de radio", names)
        self.assertTrue(all(c["distance_km"] is not None for c in data["results"]))

    def test_abiertos_segun_horario_aparecen_primero(self):
        # "Lejos" es 24 h (abierta) y "Cerca" no tiene horario (cerrada): la abierta va primero.
        data = self.client.get(self.URL, {"lat": -36.6066, "lng": -72.1034}).json()
        self.assertEqual(data["results"][0]["name"], "Lejos")
        self.assertIn("disclaimer", data)

    def test_filtros_combinables(self):
        r = self.client.get(self.URL, {"lat": -36.6066, "lng": -72.1034, "emergency": "true", "service": "GENERAL"})
        self.assertEqual([c["name"] for c in r.json()["results"]], ["Cerca"])
        r = self.client.get(self.URL, {"lat": -36.6066, "lng": -72.1034, "open_now": "true"})
        self.assertEqual([c["name"] for c in r.json()["results"]], ["Lejos"])

    def test_resultado_vacio_es_valido(self):
        r = self.client.get(self.URL, {"lat": -33.45, "lng": -70.66, "radius_km": 1})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["count"], 0)

    def test_latitud_sin_longitud_es_invalida(self):
        self.assertErrorCode(self.client.get(self.URL, {"lat": -36.6}), 400, "validation_error")

    def test_ficha_incluye_origen_y_fecha_de_verificacion(self):
        data = self.client.get(f"{self.URL}{self.near.pk}/").json()
        for key in ("info_origin", "info_origin_label", "verified_at", "is_stale", "opening_hours", "today_hours"):
            self.assertIn(key, data)
        self.assertNotIn("slots", data)  # sin cupos (Tabla 12, nivel 4 fuera de alcance)
