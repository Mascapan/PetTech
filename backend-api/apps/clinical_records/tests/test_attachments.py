"""Fotografías de la ficha (DEC-031): normalización, permisos, bitácora y vínculo con la transcripción del carnet."""
import io

from django.core.files.uploadedfile import SimpleUploadedFile
from django.urls import reverse
from django.utils import timezone
from PIL import Image

from apps.access_control import services as access
from apps.access_control.models import AuditEvent
from apps.accounts.models import User
from apps.clinical_records.models import Attachment, ClinicalEntry
from apps.core.tests.helpers import PetTechAPITestCase, link_professional, make_clinic
from apps.pets.models import Pet


def jpeg_with_gps(size=(2400, 1200)) -> bytes:
    img = Image.new("RGB", size, (200, 30, 30))
    exif = Image.Exif()
    exif[0x8825] = {2: (36.0, 36.0, 0.0)}  # GPSInfo: latitud de ejemplo
    exif[0x010F] = "TelefonoDemo"  # Make
    out = io.BytesIO()
    img.save(out, format="JPEG", exif=exif)
    return out.getvalue()


def upload(content=None, name="foto.jpg"):
    return SimpleUploadedFile(name, content or jpeg_with_gps(), content_type="image/jpeg")


class AttachmentTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.tutor = self.make_user()
        self.pet = Pet.objects.create(owner=self.tutor, name="Luna", species="DOG")
        self.clinic = make_clinic()
        self.pro = self.make_user("pro@pettech.test", role=User.Role.PROFESSIONAL, full_name="Dra. Foto")
        link_professional(self.pro, self.clinic)

    def owner_url(self, att=None):
        if att:
            return reverse("owner-attachment", args=[self.pet.pk, att])
        return reverse("owner-attachment-create", args=[self.pet.pk])

    def test_tutor_sube_foto_normalizada_sin_exif(self):
        entry = ClinicalEntry.objects.create(pet=self.pet, entry_type="VACCINE", title="Antirrábica",
                                             occurred_on=timezone.localdate(), origin="OWNER_DECLARED",
                                             created_by=self.tutor, author_name="T")
        self.authenticate(self.tutor)
        r = self.client.post(self.owner_url(), {"file": upload(), "kind": "VACCINE_LABEL", "entry": str(entry.pk)},
                             format="multipart")
        self.assertEqual(r.status_code, 201, r.content)
        att = Attachment.objects.get()
        self.assertEqual((att.width, att.height), (1600, 800))
        with Image.open(io.BytesIO(bytes(att.content))) as img:
            self.assertEqual(img.format, "JPEG")
            self.assertEqual(len(img.getexif()), 0)  # sin GPS ni modelo del teléfono
        # Se lista en la ficha y se descarga como imagen.
        record = self.client.get(reverse("owner-record", args=[self.pet.pk])).json()
        self.assertEqual(record["entries"][0]["attachments"][0]["id"], str(att.pk))
        r = self.client.get(self.owner_url(att.pk))
        self.assertEqual((r.status_code, r["Content-Type"]), (200, "image/jpeg"))
        # Duplicado rechazado.
        r = self.client.post(self.owner_url(), {"file": upload(), "kind": "VACCINE_LABEL", "entry": str(entry.pk)},
                             format="multipart")
        self.assertEqual(r.status_code, 400)

    def test_rechaza_archivos_que_no_son_imagen(self):
        self.authenticate(self.tutor)
        fake = SimpleUploadedFile("x.jpg", b"%PDF-1.4 no soy una foto", content_type="image/jpeg")
        r = self.client.post(self.owner_url(), {"file": fake, "kind": "CARD_SCAN"}, format="multipart")
        self.assertEqual(r.status_code, 400)
        self.assertFalse(Attachment.objects.exists())

    def test_aislamiento_entre_tutores(self):
        self.authenticate(self.tutor)
        r = self.client.post(self.owner_url(), {"file": upload(), "kind": "CARD_SCAN"}, format="multipart")
        att_id = r.json()["id"]
        self.authenticate(self.make_user("otro@pettech.test"))
        self.assertEqual(self.client.get(self.owner_url(att_id)).status_code, 404)

    def test_profesional_adjunta_a_su_registro_con_bitacora(self):
        req = access.request_access(self.pro, self.pet.public_code)
        access.decide(self.tutor, req.pk, approve=True)
        self.authenticate(self.pro)
        r = self.client.post(reverse("pro-entry-create", args=[self.pet.public_code]), {
            "entry_type": "VACCINE", "title": "Óctuple", "occurred_on": timezone.localdate().isoformat()})
        entry_id = r.json()["id"]
        url = reverse("pro-attachment-create", args=[self.pet.public_code])
        r = self.client.post(url, {"file": upload(), "kind": "VACCINE_LABEL", "entry": entry_id}, format="multipart")
        self.assertEqual(r.status_code, 201, r.content)
        att = Attachment.objects.get()
        self.assertEqual((att.origin, att.clinic_id), ("PROFESSIONAL", self.clinic.pk))
        self.assertTrue(AuditEvent.objects.filter(action="ATTACHMENT_ADDED", pet=self.pet).exists())
        # El tutor no puede borrar la foto de la veterinaria.
        self.authenticate(self.tutor)
        self.assertEqual(self.client.delete(self.owner_url(att.pk)).status_code, 403)
        # Revocado el acceso, el profesional ya no puede verla.
        access.revoke(self.tutor, req.pk)
        self.authenticate(self.pro)
        r = self.client.get(reverse("pro-attachment", args=[self.pet.public_code, att.pk]))
        self.assertIn(r.status_code, (403, 404))

    def test_profesional_sin_autorizacion_no_sube(self):
        self.authenticate(self.pro)
        r = self.client.post(reverse("pro-attachment-create", args=[self.pet.public_code]),
                             {"file": upload(), "kind": "VACCINE_LABEL"}, format="multipart")
        self.assertIn(r.status_code, (403, 404))

    def test_transcripcion_desde_carnet_y_borrado(self):
        self.authenticate(self.tutor)
        att_id = self.client.post(self.owner_url(), {"file": upload(), "kind": "CARD_SCAN"},
                                  format="multipart").json()["id"]
        r = self.client.post(reverse("owner-entry-create", args=[self.pet.pk]), {
            "entry_type": "VACCINE", "title": "Antirrábica (carnet)", "occurred_on": "2025-05-01",
            "source_attachment": att_id}, format="json")
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.json()["source_attachment"], att_id)
        # La foto respalda un antecedente: no se puede borrar.
        self.assertEqual(self.client.delete(self.owner_url(att_id)).status_code, 403)
        # Una foto suelta propia sí se puede borrar.
        other = self.client.post(self.owner_url(), {"file": upload(jpeg_with_gps((800, 600))), "kind": "CARD_SCAN"},
                                 format="multipart").json()["id"]
        self.assertEqual(self.client.delete(self.owner_url(other)).status_code, 204)

    def test_fuente_de_otra_mascota_es_rechazada(self):
        other_pet = Pet.objects.create(owner=self.tutor, name="Michi", species="CAT")
        self.authenticate(self.tutor)
        att_id = self.client.post(reverse("owner-attachment-create", args=[other_pet.pk]),
                                  {"file": upload(), "kind": "CARD_SCAN"}, format="multipart").json()["id"]
        r = self.client.post(reverse("owner-entry-create", args=[self.pet.pk]), {
            "entry_type": "VACCINE", "title": "X", "occurred_on": "2025-05-01", "source_attachment": att_id},
            format="json")
        self.assertEqual(r.status_code, 400)
