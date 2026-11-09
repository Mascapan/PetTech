"""Acceso de veterinarias por código PetTech con autorización del tutor (HU-16 a HU-18, HU-24, RN-01..RN-03)."""
from datetime import timedelta

from django.utils import timezone

from apps.access_control.models import AccessRequest, AuditEvent
from apps.accounts.models import User
from apps.core.tests.helpers import PetTechAPITestCase, link_professional, make_clinic
from apps.pets.models import Pet

REQ = "/api/v1/access/requests/"


class AccessFlowTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.tutor = self.make_user("tutor@pettech.test")
        self.other_tutor = self.make_user("otro@pettech.test")
        self.pet = Pet.objects.create(owner=self.tutor, name="Luna", species="DOG")
        self.clinic = make_clinic("Clínica A")
        self.clinic_b = make_clinic("Clínica B")
        self.vet = self.make_user("vet@pettech.test", role=User.Role.PROFESSIONAL, full_name="Dra. Vet")
        link_professional(self.vet, self.clinic)
        self.vet_b = self.make_user("vetb@pettech.test", role=User.Role.PROFESSIONAL)
        link_professional(self.vet_b, self.clinic_b)

    def record_url(self, code=None):
        return f"/api/v1/professional/pets/{code or self.pet.public_code}/clinical-record/"

    def request_access(self, vet=None, code=None):
        self.authenticate(vet or self.vet)
        return self.client.post(REQ, {"public_code": code or self.pet.public_code, "reason": "Control"})

    def approve(self, req_id):
        self.authenticate(self.tutor)
        return self.client.post(f"{REQ}{req_id}/approve/")

    def test_conocer_el_codigo_no_da_acceso_a_la_ficha(self):
        self.authenticate(self.vet)
        self.assertErrorCode(self.client.get(self.record_url()), 404, "not_found")
        self.assertTrue(AuditEvent.objects.filter(result="DENIED", actor=self.vet).exists())

    def test_profesional_solicita_con_codigo_tolerante_al_dictado(self):
        code = self.pet.public_code.lower().replace("-", " ")
        r = self.request_access(code=code)
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.json()["status"], "PENDING")
        self.assertIsNone(r.json()["pet_name"])  # no revela datos antes de la autorización

    def test_codigo_inexistente_y_formato_invalido(self):
        self.assertErrorCode(self.request_access(code="PT-0000-0000"), 404, "not_found")
        self.assertErrorCode(self.request_access(code="hola"), 400, "validation_error")

    def test_profesional_sin_vinculo_verificado_no_puede_solicitar(self):
        unverified = self.make_user("nv@pettech.test", role=User.Role.PROFESSIONAL)
        link_professional(unverified, self.clinic, verified=False)
        self.assertErrorCode(self.request_access(vet=unverified), 403, "permission_denied")

    def test_tutor_no_puede_solicitar_acceso(self):
        self.authenticate(self.tutor)
        r = self.client.post(REQ, {"public_code": self.pet.public_code})
        self.assertEqual(r.status_code, 403)

    def test_flujo_completo_aprobar_consultar_revocar(self):
        req_id = self.request_access().json()["id"]
        # El tutor ve quién solicita y desde qué establecimiento.
        self.authenticate(self.tutor)
        pending = self.client.get(REQ, {"status": "PENDING"}).json()
        self.assertEqual(pending[0]["clinic_name"], "Clínica A")
        self.assertEqual(pending[0]["professional_name"], "Dra. Vet")

        r = self.approve(req_id)
        self.assertEqual(r.json()["status"], "APPROVED")
        self.assertAlmostEqual(
            (AccessRequest.objects.get(pk=req_id).expires_at - timezone.now()).total_seconds(), 24 * 3600, delta=60
        )
        self.authenticate(self.vet)
        r = self.client.get(self.record_url())
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["pet"]["name"], "Luna")
        self.assertNotIn("id", r.json()["pet"])

        # Otra clínica no hereda la autorización.
        self.authenticate(self.vet_b)
        self.assertEqual(self.client.get(self.record_url()).status_code, 404)

        # Revocación con efecto inmediato.
        self.authenticate(self.tutor)
        self.assertEqual(self.client.post(f"{REQ}{req_id}/revoke/").json()["status"], "REVOKED")
        self.authenticate(self.vet)
        self.assertEqual(self.client.get(self.record_url()).status_code, 404)

        actions = set(AuditEvent.objects.filter(pet=self.pet).values_list("action", flat=True))
        self.assertTrue({"ACCESS_REQUESTED", "ACCESS_APPROVED", "RECORD_READ", "ACCESS_REVOKED"} <= actions)

    def test_autorizacion_expira(self):
        req_id = self.request_access().json()["id"]
        self.approve(req_id)
        AccessRequest.objects.filter(pk=req_id).update(expires_at=timezone.now() - timedelta(seconds=1))
        self.authenticate(self.vet)
        self.assertEqual(self.client.get(self.record_url()).status_code, 404)
        self.assertEqual(self.client.get(REQ).json()[0]["status"], "EXPIRED")

    def test_rechazo_no_concede_acceso(self):
        req_id = self.request_access().json()["id"]
        self.authenticate(self.tutor)
        self.assertEqual(self.client.post(f"{REQ}{req_id}/reject/").json()["status"], "REJECTED")
        self.assertErrorCode(self.client.post(f"{REQ}{req_id}/approve/"), 400, "validation_error")
        self.authenticate(self.vet)
        self.assertEqual(self.client.get(self.record_url()).status_code, 404)

    def test_otro_tutor_no_puede_aprobar(self):
        req_id = self.request_access().json()["id"]
        self.authenticate(self.other_tutor)
        self.assertEqual(self.client.post(f"{REQ}{req_id}/approve/").status_code, 404)
        self.assertEqual(self.client.get(REQ).json(), [])

    def test_solicitud_repetida_reutiliza_la_pendiente(self):
        first = self.request_access().json()["id"]
        second = self.request_access().json()["id"]
        self.assertEqual(first, second)

    def test_tutor_ve_bitacora_de_su_mascota(self):
        self.approve(self.request_access().json()["id"])
        self.authenticate(self.vet)
        self.client.get(self.record_url())
        self.authenticate(self.tutor)
        log = self.client.get(f"/api/v1/access/pets/{self.pet.pk}/log/").json()
        self.assertIn("RECORD_READ", [e["action"] for e in log])
        self.authenticate(self.other_tutor)
        self.assertEqual(self.client.get(f"/api/v1/access/pets/{self.pet.pk}/log/").status_code, 404)

    def test_bitacora_no_se_modifica(self):
        self.request_access()
        event = AuditEvent.objects.first()
        with self.assertRaises(ValueError):
            event.save()
        with self.assertRaises(ValueError):
            event.delete()

    def test_filtro_por_mascota_invalido_no_falla(self):
        self.request_access()
        self.authenticate(self.tutor)
        self.assertEqual(self.client.get(REQ, {"pet": "no-es-uuid"}).json(), [])
        self.assertEqual(len(self.client.get(REQ, {"pet": str(self.pet.pk)}).json()), 1)
