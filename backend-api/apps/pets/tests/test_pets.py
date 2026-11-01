"""Pruebas de mascotas: registro, validación, código público y aislamiento por tutor (HU-02, RNF-02)."""
import uuid
from datetime import timedelta
from unittest import mock

from django.urls import reverse
from django.utils import timezone

from apps.accounts.models import User
from apps.core.tests.helpers import PetTechAPITestCase
from apps.pets.codes import CODE_REGEX, normalize_public_code
from apps.pets.models import Pet

LIST = reverse("pet-list")


def detail(pet_id):
    return reverse("pet-detail", args=[pet_id])


def pet_payload(**overrides):
    data = {
        "name": "Luna",
        "species": "DOG",
        "breed": "Mestiza",
        "sex": "FEMALE",
        "birth_date": "2021-03-15",
        "birth_date_is_estimated": True,
        "microchip_number": "",
        "general_notes": "Sin antecedentes conocidos.",
    }
    data.update(overrides)
    return data


class PetCrudTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.tutor = self.make_user(email="tutora@pettech.test")
        self.authenticate(self.tutor)

    def test_anonimo_no_puede_listar_ni_crear(self):
        self.logout_client()
        self.assertErrorCode(self.client.get(LIST), 401, "not_authenticated")
        self.assertErrorCode(self.client.post(LIST, pet_payload(), format="json"), 401, "not_authenticated")

    def test_registrar_mascota_genera_codigo_publico(self):
        response = self.client.post(LIST, pet_payload(), format="json")
        self.assertEqual(response.status_code, 201, response.content)
        body = response.json()
        self.assertRegex(body["public_code"], CODE_REGEX)
        uuid.UUID(body["id"])  # identificador interno no secuencial
        self.assertEqual(body["data_origin"], "OWNER_DECLARED")
        self.assertEqual(body["species_label"], "Perro")
        pet = Pet.objects.get(pk=body["id"])
        self.assertEqual(pet.owner, self.tutor)

    def test_propietario_se_toma_del_token_no_del_cuerpo(self):
        other = self.make_user(email="otro@pettech.test")
        response = self.client.post(LIST, pet_payload(owner=str(other.pk)), format="json")
        self.assertEqual(response.status_code, 201)
        self.assertEqual(Pet.objects.get(pk=response.json()["id"]).owner, self.tutor)

    def test_campos_obligatorios(self):
        response = self.client.post(LIST, {"breed": "x"}, format="json")
        self.assertErrorCode(response, 400, "validation_error")
        fields = response.json()["error"]["fields"]
        self.assertIn("name", fields)
        self.assertIn("species", fields)

    def test_nombre_solo_espacios_es_invalido(self):
        response = self.client.post(LIST, pet_payload(name="   "), format="json")
        self.assertErrorCode(response, 400, "validation_error")

    def test_especie_fuera_del_alcance_es_invalida(self):
        response = self.client.post(LIST, pet_payload(species="HAMSTER"), format="json")
        self.assertErrorCode(response, 400, "validation_error")
        self.assertIn("species", response.json()["error"]["fields"])

    def test_fecha_de_nacimiento_futura_es_invalida(self):
        tomorrow = (timezone.localdate() + timedelta(days=1)).isoformat()
        response = self.client.post(LIST, pet_payload(birth_date=tomorrow), format="json")
        self.assertErrorCode(response, 400, "validation_error")
        self.assertIn("birth_date", response.json()["error"]["fields"])

    def test_fecha_de_nacimiento_opcional(self):
        response = self.client.post(LIST, pet_payload(birth_date=None), format="json")
        self.assertEqual(response.status_code, 201, response.content)

    def test_microchip_invalido(self):
        response = self.client.post(LIST, pet_payload(microchip_number="12345"), format="json")
        self.assertErrorCode(response, 400, "validation_error")
        self.assertIn("microchip_number", response.json()["error"]["fields"])

    def test_microchip_valido_de_15_digitos(self):
        response = self.client.post(LIST, pet_payload(microchip_number="985112003456789"), format="json")
        self.assertEqual(response.status_code, 201, response.content)

    def test_editar_mascota_propia_sin_cambiar_codigo(self):
        created = self.client.post(LIST, pet_payload(), format="json").json()
        response = self.client.patch(
            detail(created["id"]), {"name": "Luna María", "public_code": "PT-0000-0000"}, format="json"
        )
        self.assertEqual(response.status_code, 200, response.content)
        self.assertEqual(response.json()["name"], "Luna María")
        self.assertEqual(response.json()["public_code"], created["public_code"])

    def test_eliminar_mascota_propia(self):
        created = self.client.post(LIST, pet_payload(), format="json").json()
        self.assertEqual(self.client.delete(detail(created["id"])).status_code, 204)
        self.assertFalse(Pet.objects.filter(pk=created["id"]).exists())

    def test_listado_paginado(self):
        for i in range(3):
            self.client.post(LIST, pet_payload(name=f"Mascota {i}"), format="json")
        body = self.client.get(LIST).json()
        self.assertEqual(body["count"], 3)
        self.assertEqual(len(body["results"]), 3)
        self.assertIn("next", body)


class PetIsolationTests(PetTechAPITestCase):
    """Autorización a nivel de objeto: un tutor no puede ver ni modificar mascotas ajenas."""

    def setUp(self):
        super().setUp()
        self.alice = self.make_user(email="alice@pettech.test")
        self.bob = self.make_user(email="bob@pettech.test")
        self.bob_pet = Pet.objects.create(owner=self.bob, name="Rocky", species=Pet.Species.DOG)
        self.authenticate(self.alice)

    def test_listado_solo_incluye_mascotas_propias(self):
        Pet.objects.create(owner=self.alice, name="Michi", species=Pet.Species.CAT)
        names = [p["name"] for p in self.client.get(LIST).json()["results"]]
        self.assertEqual(names, ["Michi"])

    def test_no_puede_ver_mascota_ajena(self):
        self.assertErrorCode(self.client.get(detail(self.bob_pet.pk)), 404, "not_found")

    def test_no_puede_editar_mascota_ajena(self):
        response = self.client.patch(detail(self.bob_pet.pk), {"name": "Robado"}, format="json")
        self.assertErrorCode(response, 404, "not_found")
        self.bob_pet.refresh_from_db()
        self.assertEqual(self.bob_pet.name, "Rocky")

    def test_no_puede_eliminar_mascota_ajena(self):
        self.assertErrorCode(self.client.delete(detail(self.bob_pet.pk)), 404, "not_found")
        self.assertTrue(Pet.objects.filter(pk=self.bob_pet.pk).exists())

    def test_conocer_el_codigo_publico_no_da_acceso(self):
        # El código no es una credencial: no existe endpoint del tutor que permita leer por código ajeno.
        response = self.client.get(LIST, {"public_code": self.bob_pet.public_code})
        self.assertEqual(response.json()["count"], 0)

    def test_profesional_no_usa_endpoints_de_tutor(self):
        pro = self.make_user(email="vet@pettech.test", role=User.Role.PROFESSIONAL)
        self.authenticate(pro)
        self.assertErrorCode(self.client.get(LIST), 403, "permission_denied")
        self.assertErrorCode(self.client.get(detail(self.bob_pet.pk)), 403, "permission_denied")

    def test_administrador_no_tiene_acceso_a_mascotas_por_api(self):
        admin = User.objects.create_superuser(email="admin@pettech.test", password="Admin-Clave-2026", full_name="Admin")
        self.authenticate(admin)
        self.assertErrorCode(self.client.get(LIST), 403, "permission_denied")


class PublicCodeTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.tutor = self.make_user(email="codigos@pettech.test")

    def test_codigos_unicos_en_muchas_mascotas(self):
        pets = [Pet.objects.create(owner=self.tutor, name=f"M{i}", species=Pet.Species.CAT) for i in range(50)]
        codes = {p.public_code for p in pets}
        self.assertEqual(len(codes), 50)
        for code in codes:
            self.assertRegex(code, CODE_REGEX)

    def test_reintenta_si_el_codigo_colisiona(self):
        existing = Pet.objects.create(owner=self.tutor, name="Primera", species=Pet.Species.DOG)
        new_code = "PT-ZZZZ-ZZZZ"
        with mock.patch("apps.pets.models.generate_public_code", return_value=new_code):
            pet = Pet(owner=self.tutor, name="Segunda", species=Pet.Species.DOG, public_code=existing.public_code)
            pet.save()
        self.assertEqual(pet.public_code, new_code)
        self.assertEqual(Pet.objects.count(), 2)

    def test_normalizacion_de_codigo_dictado(self):
        self.assertEqual(normalize_public_code("pt-k791-9pcs"), "PT-K791-9PCS")
        self.assertEqual(normalize_public_code("PT K791 9PCS"), "PT-K791-9PCS")
        # I/L se interpretan como 1 y O como 0 (alfabeto Crockford).
        self.assertEqual(normalize_public_code("k7i19pcs"), "PT-K711-9PCS")
        self.assertEqual(normalize_public_code("PT-OOOO-LLLL"), "PT-0000-1111")
        self.assertIsNone(normalize_public_code("PT-123"))
        self.assertIsNone(normalize_public_code(""))
