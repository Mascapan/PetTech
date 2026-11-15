"""Incremento 4 (portal): página pública, cambio de contraseña, perfil ampliado y métricas de la veterinaria."""
from datetime import timedelta

from django.test import override_settings
from django.urls import reverse
from django.utils import timezone
from knox.models import AuthToken

from apps.access_control.models import AuditEvent
from apps.accounts.models import User
from apps.clinical_records.models import ClinicalEntry
from apps.clinics.models import Clinic, ProfessionalMembership
from apps.core.tests.helpers import DEFAULT_PASSWORD
from apps.pets.models import Pet

from .test_portal import NEW_PASSWORD, PortalTestCase


class LandingTests(PortalTestCase):
    @override_settings(PETTECH_APK_URL="https://example.test/PetTech.apk")
    def test_landing_publica_con_descarga_e_ingreso(self):
        r = self.client.get("/")
        self.assertEqual(r.status_code, 200)
        self.assertContains(r, "https://example.test/PetTech.apk")
        self.assertContains(r, reverse("portal:login"))
        self.assertContains(r, "1</b>")  # la clínica de prueba tiene cuenta y participa en la red

    @override_settings(PETTECH_APK_URL="")
    def test_sin_apk_muestra_proximamente(self):
        self.assertContains(self.client.get("/"), "próximamente")


class PasswordChangeTests(PortalTestCase):
    def test_cambia_contrasena_y_cierra_sesiones_de_la_app(self):
        AuthToken.objects.create(user=self.clinic_user)
        self.login(self.clinic_user)
        r = self.client.post(reverse("portal:password-change"), {
            "old_password": DEFAULT_PASSWORD, "new_password": NEW_PASSWORD, "new_password2": NEW_PASSWORD})
        self.assertRedirects(r, reverse("portal:home"), fetch_redirect_response=False)
        self.clinic_user.refresh_from_db()
        self.assertTrue(self.clinic_user.check_password(NEW_PASSWORD))
        self.assertFalse(AuthToken.objects.filter(user=self.clinic_user).exists())
        self.assertTrue(AuditEvent.objects.filter(action="PASSWORD_CHANGED", actor=self.clinic_user).exists())
        # La sesión actual sigue activa.
        self.assertEqual(self.client.get(reverse("portal:clinic-dashboard")).status_code, 200)

    def test_exige_contrasena_actual_correcta(self):
        self.login(self.admin)
        r = self.client.post(reverse("portal:password-change"), {
            "old_password": "incorrecta", "new_password": NEW_PASSWORD, "new_password2": NEW_PASSWORD})
        self.assertContains(r, "La contraseña actual no es correcta.")
        self.admin.refresh_from_db()
        self.assertTrue(self.admin.check_password(DEFAULT_PASSWORD))

    def test_rechaza_contrasena_debil_o_distinta(self):
        self.login(self.admin)
        r = self.client.post(reverse("portal:password-change"), {
            "old_password": DEFAULT_PASSWORD, "new_password": "123", "new_password2": "456"})
        self.assertContains(r, "no coinciden")

    def test_tutor_no_accede(self):
        self.login(self.tutor)
        self.assertEqual(self.client.get(reverse("portal:password-change")).status_code, 403)


class ClinicProfileV2Tests(PortalTestCase):
    def test_ubicacion_fuera_de_chile_es_rechazada(self):
        self.login(self.clinic_user)
        data = {"name": "X", "address": "Y", "commune": "Chillán", "latitude": "40.4", "longitude": "-3.7",
                "emergency_24h": "on"}
        r = self.client.post(reverse("portal:clinic-profile"), data)
        self.assertContains(r, "no parece estar en Chile")

    def test_perfil_muestra_datos_actuales(self):
        self.clinic.services_detail = "Ecografía"
        self.clinic.save()
        self.login(self.clinic_user)
        r = self.client.get(reverse("portal:clinic-profile"))
        self.assertContains(r, "Ecografía")
        self.assertContains(r, "Red PetTech")


class ClinicMetricsTests(PortalTestCase):
    def setUp(self):
        super().setUp()
        self.pro = User.objects.create_user("pro@pettech.test", DEFAULT_PASSWORD, full_name="Dr. Métrica",
                                            role=User.Role.PROFESSIONAL)
        ProfessionalMembership.objects.create(professional=self.pro, clinic=self.clinic, is_verified=True)
        cat = Pet.objects.create(owner=self.tutor, name="Michi", species="CAT")
        today = timezone.localdate()
        for pet, etype, title, days_ago in [(self.pet, "VACCINE", "Antirrábica", 3), (self.pet, "VISIT", "Control", 2),
                                            (cat, "VACCINE", "Triple felina", 1), (cat, "VACCINE", "Antirrábica", 400)]:
            ClinicalEntry.objects.create(
                pet=pet, entry_type=etype, title=title, occurred_on=today - timedelta(days=days_ago),
                origin="PROFESSIONAL", created_by=self.pro, author_name=self.pro.full_name, clinic=self.clinic,
                clinic_name=self.clinic.name, diagnosis="Sano" if etype == "VISIT" else "",
                next_due_on=today + timedelta(days=10) if title == "Antirrábica" and days_ago == 3 else None)

    def test_metricas_agregadas(self):
        self.login(self.clinic_user)
        r = self.client.get(reverse("portal:clinic-metrics"), {"dias": 90})
        self.assertEqual(r.status_code, 200)
        k = r.context["m"]["kpis"]
        self.assertEqual((k["attentions"], k["patients"], k["vaccines"], k["visits"]), (3, 2, 2, 1))
        self.assertEqual((k["new_patients"], k["returning_patients"]), (1, 1))  # Michi ya fue atendida hace 400 días
        self.assertEqual((k["upcoming_30d"], k["structured_pct"]), (1, 100))
        charts = r.context["m"]["charts"]
        self.assertEqual(charts["by_species"]["values"], [1, 1])
        self.assertEqual(charts["per_professional"]["labels"], ["Dr. Métrica"])
        # La cuenta de la veterinaria no ve nombres de mascotas.
        self.assertNotContains(r, "Michi")

    def test_periodo_invalido_usa_90_dias(self):
        self.login(self.clinic_user)
        r = self.client.get(reverse("portal:clinic-metrics"), {"dias": "abc"})
        self.assertEqual(r.context["m"]["days"], 90)

    def test_solo_rol_veterinaria(self):
        self.login(self.pro)
        self.assertEqual(self.client.get(reverse("portal:clinic-metrics")).status_code, 403)


class DirectoryAdminTests(PortalTestCase):
    def test_alta_manual_rechaza_google_como_fuente(self):
        self.login(self.admin)
        base = {"name": "Vet Pública", "address": "Calle 1", "commune": "Chillán", "latitude": "-36.6",
                "longitude": "-72.1"}
        r = self.client.post(reverse("portal:admin-directory-new"), {**base, "source_note": "Google Maps"})
        self.assertContains(r, "términos de Google Maps")
        r = self.client.post(reverse("portal:admin-directory-new"),
                             {**base, "source_note": "Sitio web oficial (consultado 15-11-2026)"})
        clinic = Clinic.objects.get(name="Vet Pública")
        self.assertRedirects(r, reverse("portal:admin-clinic", args=[clinic.pk]))
        self.assertFalse(clinic.in_pettech_network)

    def test_importa_json_de_overpass(self):
        import io
        import json

        self.login(self.admin)
        payload = {"elements": [{"type": "node", "id": 9, "lat": -36.6, "lon": -72.1,
                                 "tags": {"amenity": "veterinary", "name": "Vet OSM"}}]}
        f = io.BytesIO(json.dumps(payload).encode()); f.name = "x.json"
        r = self.client.post(reverse("portal:admin-directory-import"), {"file": f, "commune": "Chillán"})
        self.assertEqual(r.status_code, 302)
        self.assertTrue(Clinic.objects.filter(external_ref="osm:node/9").exists())
        bad = io.BytesIO(b"{}"); bad.name = "x.json"
        r = self.client.post(reverse("portal:admin-directory-import"), {"file": bad, "commune": "Chillán"}, follow=True)
        self.assertContains(r, "formato JSON de Overpass")

    def test_afiliar_veterinaria_del_directorio(self):
        from apps.core.tests.helpers import make_clinic

        directory = make_clinic("Vet Directorio", network=False)
        self.assertFalse(directory.in_pettech_network)
        self.login(self.admin)
        r = self.client.post(reverse("portal:admin-clinic-affiliate", args=[directory.pk]), {
            "account_name": "Resp", "account_email": "resp@vet.test",
            "account_password": NEW_PASSWORD, "account_password2": NEW_PASSWORD})
        self.assertRedirects(r, reverse("portal:admin-clinic", args=[directory.pk]))
        directory.refresh_from_db()
        self.assertTrue(directory.in_pettech_network)
        self.assertEqual(directory.account.role, User.Role.CLINIC)
        self.assertTrue(AuditEvent.objects.filter(action="CLINIC_AFFILIATED", clinic=directory).exists())
        # No se puede afiliar dos veces.
        r = self.client.post(reverse("portal:admin-clinic-affiliate", args=[directory.pk]), {
            "account_name": "Otro", "account_email": "otro@vet.test",
            "account_password": NEW_PASSWORD, "account_password2": NEW_PASSWORD})
        self.assertContains(r, "ya tiene una cuenta")

    def test_solo_admin(self):
        self.login(self.clinic_user)
        self.assertEqual(self.client.get(reverse("portal:admin-directory-new")).status_code, 403)


class VetPhotoTests(PortalTestCase):
    def test_profesional_registra_vacuna_con_foto_desde_el_portal(self):
        from django.core.files.uploadedfile import SimpleUploadedFile

        from apps.access_control import services as access
        from apps.clinical_records.models import Attachment
        from apps.clinical_records.tests.test_attachments import jpeg_with_gps

        pro = User.objects.create_user("vf@pettech.test", DEFAULT_PASSWORD, full_name="Dr. Foto",
                                       role=User.Role.PROFESSIONAL)
        ProfessionalMembership.objects.create(professional=pro, clinic=self.clinic, is_verified=True)
        req = access.request_access(pro, self.pet.public_code)
        access.decide(self.tutor, req.pk, approve=True)
        self.login(pro)
        url = reverse("portal:vet-record", args=[self.pet.public_code])
        base = {"entry_type": "VACCINE", "title": "Antirrábica", "occurred_on": timezone.localdate().isoformat(),
                "lot_number": "L9", "manufacturer": "Lab"}
        bad = SimpleUploadedFile("x.jpg", b"no-imagen", content_type="image/jpeg")
        r = self.client.post(url, {**base, "label_photo": bad})
        self.assertEqual(r.status_code, 400)
        self.assertFalse(ClinicalEntry.objects.exists())  # no se guarda el registro si la foto es inválida
        r = self.client.post(url, {**base, "label_photo": SimpleUploadedFile("e.jpg", jpeg_with_gps(), "image/jpeg")})
        self.assertEqual(r.status_code, 302)
        att = Attachment.objects.get()
        self.assertEqual(att.entry.title, "Antirrábica")
        page = self.client.get(url)
        img_url = reverse("portal:vet-attachment", args=[self.pet.public_code, att.pk])
        self.assertContains(page, img_url)
        self.assertEqual(self.client.get(img_url)["Content-Type"], "image/jpeg")
        self.assertTrue(AuditEvent.objects.filter(action="ATTACHMENT_READ", pet=self.pet).exists())
        # Otro profesional sin autorización no ve la foto.
        other = User.objects.create_user("vf2@pettech.test", DEFAULT_PASSWORD, full_name="X", role=User.Role.PROFESSIONAL)
        self.login(other)
        self.assertEqual(self.client.get(img_url).status_code, 404)
