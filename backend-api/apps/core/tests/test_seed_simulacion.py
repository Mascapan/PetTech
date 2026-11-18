import tempfile
from io import StringIO
from pathlib import Path
from unittest import mock

from PIL import Image

from django.contrib.auth.hashers import identify_hasher
from django.core.management import call_command
from django.test import TestCase

from apps.accounts.models import User
from apps.access_control.models import AccessRequest
from apps.clinical_records.models import Attachment, ClinicalEntry, Origin
from apps.clinics.models import Clinic, ProfessionalMembership
from apps.core.management.commands.seed_simulacion import _all_accounts, _load
from apps.pets.models import Pet


def run(*args):
    call_command("seed_simulacion", *args, stdout=StringIO(), stderr=StringIO())


class SeedSimulacionTests(TestCase):
    def test_datos_sin_contrasenas_en_claro(self):
        for acc in _all_accounts(_load()):
            self.assertEqual(identify_hasher(acc["hash"]).algorithm, "argon2")
            self.assertNotIn("password", acc)

    def test_crea_red_profesionales_y_tutores(self):
        run()
        clinics = Clinic.objects.filter(external_ref__startswith="sim:")
        self.assertEqual(clinics.count(), 2)
        self.assertTrue(all(c.in_pettech_network for c in clinics))
        self.assertEqual(ProfessionalMembership.objects.filter(clinic__in=clinics, is_verified=True).count(), 4)
        self.assertEqual(User.objects.filter(email__endswith=".pettech@gmail.com", role=User.Role.TUTOR).count(), 6)
        self.assertEqual(Pet.objects.filter(owner__email__endswith=".pettech@gmail.com").count(), 13)
        tutor = User.objects.get(email="valentina.rojas.pettech@gmail.com")
        self.assertTrue(tutor.has_usable_password())
        self.assertTrue(ClinicalEntry.objects.filter(pet__owner=tutor, next_due_on__isnull=False).exists())

    def test_idempotente_y_respeta_contrasena_cambiada(self):
        run()
        user = User.objects.get(email="fmunoz.vetlosrobles@gmail.com")
        user.set_password("Otra-Clave-Larga-2026")
        user.save()
        run()
        self.assertEqual(Clinic.objects.filter(external_ref__startswith="sim:").count(), 2)
        self.assertEqual(Pet.objects.filter(owner__email__endswith=".pettech@gmail.com").count(), 13)
        user.refresh_from_db()
        self.assertTrue(user.check_password("Otra-Clave-Larga-2026"))

    def test_remove_elimina_todo(self):
        run()
        run("--remove")
        self.assertFalse(Clinic.objects.filter(external_ref__startswith="sim:").exists())
        self.assertFalse(User.objects.filter(email__endswith=".pettech@gmail.com").exists())
        self.assertFalse(Pet.objects.filter(name="Canela", owner__email__endswith="@gmail.com").exists())

    def test_consultas_profesionales_sin_accesos_vigentes(self):
        run()
        pro = ClinicalEntry.objects.filter(origin=Origin.PROFESSIONAL, clinic__external_ref__startswith="sim:")
        self.assertGreater(pro.count(), 40)
        self.assertTrue(pro.exclude(temperature_c=None).exists())
        self.assertFalse(any(r.is_active_grant for r in AccessRequest.objects.all()))
        before = pro.count()
        run()
        self.assertEqual(pro.count(), before)
        from apps.portal.analytics import clinic_attention_metrics
        clinic = Clinic.objects.get(external_ref="sim:clinica-los-robles")
        self.assertTrue(clinic_attention_metrics(clinic, 365))

    def test_fotos_desde_carpeta(self):
        with tempfile.TemporaryDirectory() as tmp:
            Image.new("RGB", (900, 700), (200, 150, 90)).save(Path(tmp) / "Canela.jpg")
            with mock.patch("apps.core.management.commands.seed_simulacion.PHOTOS_DIR", Path(tmp)):
                run()
                run()
        photos = Attachment.objects.filter(kind=Attachment.Kind.PET_PHOTO)
        self.assertEqual(photos.count(), 1)
        self.assertEqual(photos.get().pet.name, "Canela")
        run("--remove")
        self.assertFalse(Attachment.objects.exists())
