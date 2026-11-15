"""Portal web (DEC-025): roles, flujo administrador → veterinaria → profesional → tutor (app) y seguridad."""
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from apps.access_control import services as access
from apps.access_control.models import AccessRequest, AuditEvent
from apps.accounts.models import User
from apps.clinical_records.models import ClinicalEntry
from apps.clinics.models import Clinic, ProfessionalMembership
from apps.core.tests.helpers import DEFAULT_PASSWORD, make_clinic
from apps.pets.models import Pet

NEW_PASSWORD = "Clave-Portal-2026"


class PortalTestCase(TestCase):
    def setUp(self):
        cache.clear()
        self.admin = User.objects.create_user("admin@pettech.test", DEFAULT_PASSWORD, full_name="Admin",
                                              role=User.Role.ADMIN)
        self.clinic_user = User.objects.create_user("clinica@pettech.test", DEFAULT_PASSWORD, full_name="Clínica",
                                                    role=User.Role.CLINIC)
        self.clinic = make_clinic("Clínica Portal", account=self.clinic_user)
        self.tutor = User.objects.create_user("tutor@pettech.test", DEFAULT_PASSWORD, full_name="Tutora")
        self.pet = Pet.objects.create(owner=self.tutor, name="Luna", species="DOG")

    def login(self, user):
        self.client.force_login(user)


class LoginTests(PortalTestCase):
    def test_redirige_a_ingresar_sin_sesion(self):
        r = self.client.get(reverse("portal:admin-dashboard"))
        self.assertRedirects(r, f"{reverse('portal:login')}?next={reverse('portal:admin-dashboard')}")

    def test_ingreso_por_rol(self):
        r = self.client.post(reverse("portal:login"), {"email": "ADMIN@pettech.test", "password": DEFAULT_PASSWORD})
        self.assertRedirects(r, reverse("portal:home"), target_status_code=302)
        self.assertRedirects(self.client.get(reverse("portal:home")), reverse("portal:admin-dashboard"))

    def test_tutor_no_usa_el_portal(self):
        r = self.client.post(reverse("portal:login"), {"email": "tutor@pettech.test", "password": DEFAULT_PASSWORD})
        self.assertContains(r, "app móvil")
        self.assertNotIn("_auth_user_id", self.client.session)

    def test_limite_de_intentos(self):
        for _ in range(8):
            self.client.post(reverse("portal:login"), {"email": "admin@pettech.test", "password": "mala"})
        r = self.client.post(reverse("portal:login"), {"email": "admin@pettech.test", "password": DEFAULT_PASSWORD})
        self.assertEqual(r.status_code, 429)

    def test_cerrar_sesion_solo_por_post(self):
        self.login(self.admin)
        self.assertEqual(self.client.get(reverse("portal:logout")).status_code, 405)
        self.client.post(reverse("portal:logout"))
        self.assertNotIn("_auth_user_id", self.client.session)


class RoleIsolationTests(PortalTestCase):
    def test_cada_rol_solo_ve_su_seccion(self):
        cases = [
            (self.clinic_user, "portal:admin-dashboard"),
            (self.admin, "portal:clinic-dashboard"),
            (self.admin, "portal:vet-home"),
            (self.clinic_user, "portal:vet-home"),
        ]
        for user, url in cases:
            self.login(user)
            self.assertEqual(self.client.get(reverse(url)).status_code, 403, (user.role, url))

    def test_administrador_no_ve_fichas_clinicas(self):
        self.login(self.admin)
        r = self.client.get(reverse("portal:vet-record", args=[self.pet.public_code]))
        self.assertEqual(r.status_code, 403)

    def test_veterinaria_suspendida_no_ingresa(self):
        self.clinic.is_active = False
        self.clinic.save()
        self.login(self.clinic_user)
        self.assertEqual(self.client.get(reverse("portal:clinic-dashboard")).status_code, 403)


class AdminTests(PortalTestCase):
    def test_tablero_de_analisis(self):
        self.login(self.admin)
        r = self.client.get(reverse("portal:admin-dashboard"))
        self.assertContains(r, "Cuadros de análisis")
        self.assertContains(r, 'id="charts"')

    def test_crear_veterinaria_con_cuenta(self):
        self.login(self.admin)
        r = self.client.post(reverse("portal:admin-clinic-new"), {
            "name": "Vet Nueva", "address": "Calle 1", "commune": "Chillán", "latitude": "-36.6",
            "longitude": "-72.1", "services": ["GENERAL"], "accepts_pettech_records": "on",
            "account_name": "Responsable", "account_email": "nueva@pettech.test",
            "account_password": NEW_PASSWORD, "account_password2": NEW_PASSWORD,
        })
        clinic = Clinic.objects.get(name="Vet Nueva")
        self.assertRedirects(r, reverse("portal:admin-clinic", args=[clinic.pk]))
        self.assertEqual(clinic.account.role, User.Role.CLINIC)
        self.assertEqual(clinic.info_origin, Clinic.InfoOrigin.PUBLIC_UNCONFIRMED)
        self.assertTrue(AuditEvent.objects.filter(action="CLINIC_CREATED").exists())

    def test_crear_veterinaria_valida_correo_y_clave(self):
        self.login(self.admin)
        r = self.client.post(reverse("portal:admin-clinic-new"), {
            "name": "X", "address": "Y", "commune": "Z", "latitude": "-36", "longitude": "-72",
            "account_name": "R", "account_email": "tutor@pettech.test",
            "account_password": "123", "account_password2": "456",
        })
        self.assertEqual(r.status_code, 200)
        self.assertContains(r, "Ya existe una cuenta con ese correo.")
        self.assertFalse(Clinic.objects.filter(name="X").exists())

    def test_suspender_veterinaria_desactiva_su_cuenta(self):
        self.login(self.admin)
        self.client.post(reverse("portal:admin-clinic", args=[self.clinic.pk]), {"action": "toggle_active"})
        self.clinic_user.refresh_from_db()
        self.assertFalse(self.clinic_user.is_active)


class ClinicAndProfessionalFlowTests(PortalTestCase):
    def create_pro(self):
        self.login(self.clinic_user)
        r = self.client.post(reverse("portal:clinic-pro-new"), {
            "full_name": "Dra. Portal", "email": "pro@pettech.test", "license_number": "123",
            "password": NEW_PASSWORD, "password2": NEW_PASSWORD,
        })
        self.assertRedirects(r, reverse("portal:clinic-dashboard"))
        return User.objects.get(email="pro@pettech.test")

    def test_flujo_completo_portal_y_app(self):
        pro = self.create_pro()
        self.assertTrue(ProfessionalMembership.objects.get(professional=pro).is_verified)

        # El profesional ingresa el código en el portal.
        self.client.force_login(pro)
        r = self.client.post(reverse("portal:vet-home"), {"public_code": self.pet.public_code.lower(), "reason": "Control"})
        self.assertRedirects(r, reverse("portal:vet-home"))
        req = AccessRequest.objects.get(pet=self.pet)
        # Sin autorización del tutor no se abre la ficha.
        r = self.client.get(reverse("portal:vet-record", args=[self.pet.public_code]))
        self.assertRedirects(r, reverse("portal:vet-home"))

        # El tutor autoriza desde la app (misma lógica que la API).
        access.decide(self.tutor, req.pk, approve=True)
        r = self.client.get(reverse("portal:vet-record", args=[self.pet.public_code]))
        self.assertContains(r, "Luna")
        r = self.client.post(reverse("portal:vet-record", args=[self.pet.public_code]), {
            "entry_type": "VACCINE", "title": "Antirrábica", "occurred_on": timezone.localdate().isoformat(),
            "next_due_on": "", "weight_kg": "", "lot_number": "L1", "notes": "",
        })
        self.assertEqual(r.status_code, 400)  # ficha estandarizada: vacuna sin laboratorio se rechaza
        self.assertContains(r, "Indica el laboratorio.", status_code=400)
        r = self.client.post(reverse("portal:vet-record", args=[self.pet.public_code]), {
            "entry_type": "VACCINE", "title": "Antirrábica", "occurred_on": timezone.localdate().isoformat(),
            "lot_number": "L1", "manufacturer": "Lab Demo", "route": "SC",
            "anamnesis": "no aplica a vacunas y se descarta",
        })
        self.assertEqual(r.status_code, 302)
        entry = ClinicalEntry.objects.get(pet=self.pet)
        self.assertEqual((entry.origin, entry.clinic_name, entry.author_name), ("PROFESSIONAL", "Clínica Portal", "Dra. Portal"))
        self.assertEqual((entry.manufacturer, entry.route, entry.anamnesis), ("Lab Demo", "SC", ""))

        # Corrección versionada desde el portal.
        r = self.client.post(reverse("portal:vet-correct", args=[self.pet.public_code, entry.pk]), {
            "entry_type": "VACCINE", "title": "Antirrábica (corregida)", "occurred_on": timezone.localdate().isoformat(),
            "lot_number": "L1", "manufacturer": "Lab Demo", "reason": "Error de digitación",
        })
        self.assertEqual(r.status_code, 302)
        self.assertEqual(ClinicalEntry.objects.filter(pet=self.pet).count(), 2)

        # La veterinaria suspende al profesional: pierde el acceso de inmediato.
        self.client.force_login(self.clinic_user)
        m = ProfessionalMembership.objects.get(professional=pro)
        self.client.post(reverse("portal:clinic-pro-toggle", args=[m.pk]), {"authorize": "0"})
        self.client.force_login(pro)
        r = self.client.get(reverse("portal:vet-record", args=[self.pet.public_code]))
        self.assertRedirects(r, reverse("portal:vet-home"))

    def test_registro_invalido_muestra_errores(self):
        pro = self.create_pro()
        req = access.request_access(pro, self.pet.public_code)
        access.decide(self.tutor, req.pk, approve=True)
        self.client.force_login(pro)
        r = self.client.post(reverse("portal:vet-record", args=[self.pet.public_code]), {
            "entry_type": "WEIGHT", "title": "", "occurred_on": "2999-01-01",
        })
        self.assertEqual(r.status_code, 400)
        self.assertContains(r, "posterior a hoy", status_code=400)

    def test_veterinaria_no_toca_profesionales_ajenos(self):
        other_user = User.objects.create_user("otra@pettech.test", DEFAULT_PASSWORD, full_name="Otra",
                                              role=User.Role.CLINIC)
        other = make_clinic("Otra", account=other_user)
        pro = User.objects.create_user("p2@pettech.test", DEFAULT_PASSWORD, full_name="P2", role=User.Role.PROFESSIONAL)
        m = ProfessionalMembership.objects.create(professional=pro, clinic=other, is_verified=True)
        self.login(self.clinic_user)
        self.client.post(reverse("portal:clinic-pro-toggle", args=[m.pk]), {"authorize": "0"})
        m.refresh_from_db()
        self.assertTrue(m.is_verified)

    def test_veterinaria_publica_su_horario(self):
        self.login(self.clinic_user)
        data = {"name": "Clínica Renovada", "address": "Av. Libertad 100", "commune": "Chillán",
                "latitude": "-36.6066", "longitude": "-72.1034", "contact_email": "hola@clinica.test",
                "services_detail": "Dermatología\nEcografía abdominal",
                "phone": "+56 9 1111 1111", "attends_emergencies": "on", "services": ["GENERAL", "SURGERY"]}
        for d in range(7):
            data[f"d{d}-open_day"] = "on" if d < 5 else ""
            data[f"d{d}-opens_at"] = "09:00" if d < 5 else ""
            data[f"d{d}-closes_at"] = "19:00" if d < 5 else ""
        r = self.client.post(reverse("portal:clinic-profile"), data)
        self.assertRedirects(r, reverse("portal:clinic-profile"))
        self.clinic.refresh_from_db()
        self.assertEqual(self.clinic.info_origin, Clinic.InfoOrigin.CONFIRMED)
        self.assertEqual(self.clinic.opening_hours.count(), 5)
        self.assertEqual((self.clinic.name, self.clinic.services_detail), ("Clínica Renovada", "Dermatología\nEcografía abdominal"))
        self.assertAlmostEqual(self.clinic.location.y, -36.6066)
        self.assertTrue(AuditEvent.objects.filter(action="CATALOG_UPDATED", clinic=self.clinic).exists())

    def test_horario_incompleto_es_rechazado(self):
        self.login(self.clinic_user)
        data = {"d0-open_day": "on", "d0-opens_at": "09:00"}
        r = self.client.post(reverse("portal:clinic-profile"), data)
        self.assertContains(r, "Indica hora de apertura y cierre.")

    def test_csrf_activo_en_formularios(self):
        from django.test import Client
        c = Client(enforce_csrf_checks=True)
        c.force_login(self.clinic_user)
        r = c.post(reverse("portal:clinic-pro-new"), {"full_name": "X"})
        self.assertEqual(r.status_code, 403)


class PublicPagesTests(TestCase):
    def test_paginas_publicas_sin_sesion(self):
        for name in ("public-privacy", "public-account-deletion"):
            r = self.client.get(reverse(name))
            self.assertEqual(r.status_code, 200)
        self.assertContains(self.client.get(reverse("public-privacy")), "Ley N.º 21.719")
