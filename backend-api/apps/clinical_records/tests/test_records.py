"""Ficha clínica estandarizada, origen de los datos y correcciones versionadas (HU-03, HU-04, HU-19..HU-23)."""
from datetime import timedelta

from django.utils import timezone

from apps.access_control import services as access
from apps.accounts.models import User
from apps.clinical_records.models import ClinicalEntry
from apps.core.tests.helpers import PetTechAPITestCase, link_professional, make_clinic
from apps.pets.models import Pet


class ClinicalRecordTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.tutor = self.make_user("tutor@pettech.test", full_name="Tutora Prueba")
        self.pet = Pet.objects.create(owner=self.tutor, name="Luna", species="DOG")
        self.clinic = make_clinic("Clínica A")
        self.clinic_b = make_clinic("Clínica B")
        self.vet = self.make_user("vet@pettech.test", role=User.Role.PROFESSIONAL, full_name="Dra. Vet")
        self.vet_b = self.make_user("vetb@pettech.test", role=User.Role.PROFESSIONAL)
        link_professional(self.vet, self.clinic)
        link_professional(self.vet_b, self.clinic_b)
        for vet in (self.vet, self.vet_b):
            req = access.request_access(vet, self.pet.public_code)
            access.decide(self.tutor, req.pk, approve=True)
        self.owner_url = f"/api/v1/pets/{self.pet.pk}/clinical-record/"
        self.pro_url = f"/api/v1/professional/pets/{self.pet.public_code}/clinical-record/"
        self.today = timezone.localdate()

    def vaccine(self, **extra):
        return {"entry_type": "VACCINE", "title": "Antirrábica", "occurred_on": str(self.today),
                "next_due_on": str(self.today + timedelta(days=365)), **extra}

    def test_tutor_registra_antecedente_declarado(self):
        self.authenticate(self.tutor)
        r = self.client.post(self.owner_url + "entries/", self.vaccine())
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.json()["origin"], "OWNER_DECLARED")

    def test_no_acepta_fechas_futuras_ni_proxima_anterior(self):
        self.authenticate(self.tutor)
        r = self.client.post(self.owner_url + "entries/", self.vaccine(occurred_on=str(self.today + timedelta(days=1))))
        self.assertIn("occurred_on", r.json()["error"]["fields"])
        r = self.client.post(self.owner_url + "entries/", self.vaccine(next_due_on=str(self.today - timedelta(days=1))))
        self.assertIn("next_due_on", r.json()["error"]["fields"])

    def test_peso_obligatorio_en_registro_de_peso(self):
        self.authenticate(self.tutor)
        r = self.client.post(self.owner_url + "entries/", {"entry_type": "WEIGHT", "title": "Peso",
                                                           "occurred_on": str(self.today)})
        self.assertIn("weight_kg", r.json()["error"]["fields"])

    def test_profesional_registra_y_el_tutor_ve_quien_donde_cuando(self):
        self.authenticate(self.vet)
        r = self.client.post(self.pro_url + "entries/", {"entry_type": "ALLERGY", "title": "Penicilina",
                                                         "occurred_on": str(self.today)})
        self.assertEqual(r.status_code, 201, r.content)
        self.authenticate(self.tutor)
        data = self.client.get(self.owner_url).json()
        entry = data["entries"][0]
        self.assertEqual((entry["origin"], entry["author_name"], entry["clinic_name"]),
                         ("PROFESSIONAL", "Dra. Vet", "Clínica A"))
        self.assertEqual(data["summary"]["allergies"], ["Penicilina"])

    def test_historial_multiclinica_y_filtro_por_tipo(self):
        self.authenticate(self.vet)
        self.client.post(self.pro_url + "entries/", self.vaccine())
        self.authenticate(self.vet_b)
        self.client.post(self.pro_url + "entries/", {"entry_type": "VISIT", "title": "Control",
                                                     "occurred_on": str(self.today), "weight_kg": 12.5})
        data = self.client.get(self.pro_url).json()
        self.assertEqual({e["clinic_name"] for e in data["entries"]}, {"Clínica A", "Clínica B"})
        self.assertEqual(data["summary"]["latest_weight_kg"], 12.5)
        only = self.client.get(self.pro_url, {"type": "VACCINE"}).json()["entries"]
        self.assertEqual([e["entry_type"] for e in only], ["VACCINE"])

    def test_correccion_conserva_version_anterior(self):
        self.authenticate(self.vet)
        entry_id = self.client.post(self.pro_url + "entries/", self.vaccine()).json()["id"]
        r = self.client.post(f"{self.pro_url}entries/{entry_id}/correct/",
                             self.vaccine(title="Antirrábica (lote corregido)", reason="Error de digitación"))
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(ClinicalEntry.objects.filter(pet=self.pet).count(), 2)
        entries = self.client.get(self.pro_url).json()["entries"]
        self.assertEqual([e["title"] for e in entries], ["Antirrábica (lote corregido)"])
        # Una versión ya corregida no se vuelve a corregir.
        r = self.client.post(f"{self.pro_url}entries/{entry_id}/correct/", self.vaccine(reason="Otra vez"))
        self.assertEqual(r.status_code, 403)

    def test_otra_clinica_no_corrige_registros_ajenos(self):
        self.authenticate(self.vet)
        entry_id = self.client.post(self.pro_url + "entries/", self.vaccine()).json()["id"]
        self.authenticate(self.vet_b)
        r = self.client.post(f"{self.pro_url}entries/{entry_id}/correct/", self.vaccine(reason="Intento ajeno"))
        self.assertEqual(r.status_code, 403)

    def test_tutor_no_corrige_registros_profesionales_pero_si_los_propios(self):
        self.authenticate(self.vet)
        pro_id = self.client.post(self.pro_url + "entries/", self.vaccine()).json()["id"]
        self.authenticate(self.tutor)
        r = self.client.post(f"{self.owner_url}entries/{pro_id}/correct/", self.vaccine(reason="No me gusta"))
        self.assertEqual(r.status_code, 403)
        own_id = self.client.post(self.owner_url + "entries/", self.vaccine()).json()["id"]
        r = self.client.post(f"{self.owner_url}entries/{own_id}/correct/", self.vaccine(reason="Fecha mal puesta"))
        self.assertEqual(r.status_code, 201)
        history = self.client.get(f"{self.owner_url}entries/{r.json()['id']}/history/").json()
        self.assertEqual(len(history), 2)

    def test_registros_son_inmutables_en_el_modelo(self):
        self.authenticate(self.vet)
        entry = ClinicalEntry.objects.get(pk=self.client.post(self.pro_url + "entries/", self.vaccine()).json()["id"])
        entry.title = "cambio silencioso"
        with self.assertRaises(ValueError):
            entry.save()
        with self.assertRaises(ValueError):
            entry.delete()

    def test_tutor_ajeno_y_administrador_sin_acceso(self):
        self.authenticate(self.make_user("otro@pettech.test"))
        self.assertEqual(self.client.get(self.owner_url).status_code, 404)
        self.authenticate(self.make_user("admin@pettech.test", role=User.Role.ADMIN))
        self.assertEqual(self.client.get(self.owner_url).status_code, 403)
        self.assertEqual(self.client.get(self.pro_url).status_code, 403)

    def test_mascota_con_registros_profesionales_no_se_elimina(self):
        self.authenticate(self.vet)
        self.client.post(self.pro_url + "entries/", self.vaccine())
        self.authenticate(self.tutor)
        self.assertErrorCode(self.client.delete(f"/api/v1/pets/{self.pet.pk}/"), 409, "conflict")

    def test_mascota_solo_con_declarados_se_elimina(self):
        pet = Pet.objects.create(owner=self.tutor, name="Michi", species="CAT")
        self.authenticate(self.tutor)
        url = f"/api/v1/pets/{pet.pk}/clinical-record/"
        own_id = self.client.post(url + "entries/", self.vaccine()).json()["id"]
        self.client.post(f"{url}entries/{own_id}/correct/", self.vaccine(reason="Ajuste de fecha"))
        self.assertEqual(self.client.delete(f"/api/v1/pets/{pet.pk}/").status_code, 204)
        self.assertFalse(ClinicalEntry.objects.filter(pet_id=pet.pk).exists())
