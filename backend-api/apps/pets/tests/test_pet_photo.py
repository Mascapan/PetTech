"""Foto de perfil de la mascota en el servidor (DEC-031)."""
from django.core.files.uploadedfile import SimpleUploadedFile
from django.urls import reverse
from django.utils import timezone

from apps.clinical_records.models import Attachment, ClinicalEntry
from apps.clinical_records.tests.test_attachments import jpeg_with_gps
from apps.core.tests.helpers import PetTechAPITestCase
from apps.pets.models import Pet


def img(size=(900, 900)):
    return SimpleUploadedFile("p.jpg", jpeg_with_gps(size), content_type="image/jpeg")


class PetPhotoTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.tutor = self.make_user()
        self.pet = Pet.objects.create(owner=self.tutor, name="Luna", species="DOG")
        self.url = reverse("pet-photo", args=[self.pet.pk])
        self.authenticate(self.tutor)

    def test_subir_reemplazar_ver_y_quitar(self):
        self.assertEqual(self.client.get(self.url).status_code, 404)
        r = self.client.put(self.url, {"file": img()}, format="multipart")
        self.assertEqual(r.status_code, 200, r.content)
        first = r.json()["photo_id"]
        self.assertEqual(self.client.get(reverse("pet-detail", args=[self.pet.pk])).json()["photo_id"], first)
        r = self.client.put(self.url, {"file": img((600, 400))}, format="multipart")
        self.assertNotEqual(r.json()["photo_id"], first)
        self.assertEqual(Attachment.objects.filter(pet=self.pet, kind="PET_PHOTO").count(), 1)  # se reemplaza
        r = self.client.get(self.url)
        self.assertEqual((r.status_code, r["Content-Type"]), (200, "image/jpeg"))
        self.assertEqual(self.client.delete(self.url).status_code, 204)
        self.assertIsNone(self.client.get(reverse("pet-detail", args=[self.pet.pk])).json()["photo_id"])

    def test_imagen_invalida_no_borra_la_anterior(self):
        self.client.put(self.url, {"file": img()}, format="multipart")
        bad = SimpleUploadedFile("x.jpg", b"no-imagen", content_type="image/jpeg")
        self.assertEqual(self.client.put(self.url, {"file": bad}, format="multipart").status_code, 400)
        self.assertEqual(Attachment.objects.filter(pet=self.pet, kind="PET_PHOTO").count(), 1)

    def test_otro_tutor_no_accede(self):
        self.client.put(self.url, {"file": img()}, format="multipart")
        self.authenticate(self.make_user("otro@pettech.test"))
        self.assertEqual(self.client.get(self.url).status_code, 404)
        self.assertEqual(self.client.put(self.url, {"file": img()}, format="multipart").status_code, 404)

    def test_eliminar_mascota_con_fotos_y_registros_propios(self):
        entry = ClinicalEntry.objects.create(pet=self.pet, entry_type="VACCINE", title="X", occurred_on=timezone.localdate(),
                                             origin="OWNER_DECLARED", created_by=self.tutor, author_name="T")
        self.client.put(self.url, {"file": img()}, format="multipart")
        self.client.post(reverse("owner-attachment-create", args=[self.pet.pk]),
                         {"file": img((500, 500)), "kind": "VACCINE_LABEL", "entry": str(entry.pk)}, format="multipart")
        scan = self.client.post(reverse("owner-attachment-create", args=[self.pet.pk]),
                                {"file": img((700, 500)), "kind": "CARD_SCAN"}, format="multipart").json()["id"]
        self.client.post(reverse("owner-entry-create", args=[self.pet.pk]), {
            "entry_type": "VACCINE", "title": "Y", "occurred_on": "2025-01-01", "source_attachment": scan}, format="json")
        r = self.client.delete(reverse("pet-detail", args=[self.pet.pk]))
        self.assertEqual(r.status_code, 204, r.content)
        self.assertFalse(Attachment.objects.exists())
