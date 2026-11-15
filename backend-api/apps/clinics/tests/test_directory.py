"""Directorio no afiliado (OpenStreetMap / fuente pública) y distintivo Red PetTech (DEC-029)."""
from datetime import time

from django.urls import reverse

from apps.accounts.models import User
from apps.clinics.directory import import_osm, parse_opening_hours
from apps.clinics.models import Clinic
from apps.core.tests.helpers import PetTechAPITestCase, make_clinic

OSM = [
    {"type": "node", "id": 1, "lat": -36.60, "lon": -72.10,
     "tags": {"amenity": "veterinary", "name": "Vet OSM Uno", "addr:street": "Calle Falsa", "addr:housenumber": "123",
              "phone": "+56 42 000 0000", "opening_hours": "Mo-Fr 09:00-19:00; Sa 10:00-14:00"}},
    {"type": "way", "id": 2, "center": {"lat": -36.61, "lon": -72.11},
     "tags": {"amenity": "veterinary", "name": "Vet OSM 24", "opening_hours": "24/7"}},
    {"type": "node", "id": 3, "lat": -36.62, "lon": -72.12, "tags": {"amenity": "veterinary"}},  # sin nombre
    {"type": "node", "id": 4, "lat": -36.62, "lon": -72.12, "tags": {"amenity": "pharmacy", "name": "Farmacia"}},
]


class OpeningHoursParserTests(PetTechAPITestCase):
    def test_formatos_simples(self):
        slots, h24 = parse_opening_hours("Mo-Fr 09:00-19:00; Sa 10:00-14:00")
        self.assertFalse(h24)
        self.assertEqual(slots[0], [(time(9), time(19))])
        self.assertEqual(slots[5], [(time(10), time(14))])
        self.assertNotIn(6, slots)
        self.assertEqual(parse_opening_hours("24/7"), ({}, True))
        self.assertEqual(parse_opening_hours("Mo,We 09:00-13:00,15:00-19:00")[0][2],
                         [(time(9), time(13)), (time(15), time(19))])

    def test_formatos_no_reconocidos_se_ignoran(self):
        for value in ["", "sunrise-sunset", "Mo-Fr 9am-5pm", "PH off; Xx 09:00-10:00"]:
            self.assertIsNone(parse_opening_hours(value), value)


class ImportTests(PetTechAPITestCase):
    def test_importa_sin_duplicar_y_con_atribucion(self):
        stats = import_osm(OSM)
        self.assertEqual(stats, {"created": 2, "updated": 0, "skipped": 2})
        uno = Clinic.objects.get(external_ref="osm:node/1")
        self.assertEqual((uno.address, uno.info_origin, uno.accepts_pettech_records, uno.account),
                         ("Calle Falsa 123", "PUBLIC_UNCONFIRMED", False, None))
        self.assertIn("OpenStreetMap", uno.source_note)
        self.assertEqual(uno.opening_hours.count(), 6)
        self.assertTrue(Clinic.objects.get(external_ref="osm:way/2").emergency_24h)
        self.assertEqual(import_osm(OSM)["updated"], 2)  # idempotente
        self.assertEqual(Clinic.objects.count(), 2)

    def test_no_pisa_datos_confirmados_ni_duplica_por_nombre(self):
        make_clinic("Vet OSM Uno")  # ya existe (afiliada) con el mismo nombre
        stats = import_osm(OSM[:1])
        self.assertEqual(stats["skipped"], 1)
        self.assertEqual(Clinic.objects.filter(name="Vet OSM Uno").count(), 1)


class NetworkBadgeTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.tutor = self.make_user()
        account = self.make_user("clinica@pettech.test", role=User.Role.CLINIC)
        self.red = make_clinic("Afiliada", account=account)
        self.dir = make_clinic("Directorio", network=False, lat=-36.607, lng=-72.104)
        self.authenticate(self.tutor)

    def test_api_distingue_red_pettech_y_filtra(self):
        r = self.client.get(reverse("clinic-search"),
                            {"lat": -36.6066, "lng": -72.1034})
        data = {c["name"]: c for c in r.json()["results"]}
        self.assertTrue(data["Afiliada"]["in_pettech_network"])
        self.assertFalse(data["Directorio"]["in_pettech_network"])
        r = self.client.get(reverse("clinic-search"), {"lat": -36.6066, "lng": -72.1034, "network": "true"})
        self.assertEqual([c["name"] for c in r.json()["results"]], ["Afiliada"])

