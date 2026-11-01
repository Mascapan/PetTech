"""Pruebas de registro, autenticación y sesiones (HU-01, RNF-01)."""
from datetime import timedelta

from django.urls import reverse
from django.utils import timezone
from knox.models import AuthToken

from apps.accounts.models import User
from apps.accounts.services import issue_token
from apps.core.tests.helpers import DEFAULT_PASSWORD, PetTechAPITestCase

REGISTER = reverse("auth-register")
LOGIN = reverse("auth-login")
LOGOUT = reverse("auth-logout")
LOGOUT_ALL = reverse("auth-logout-all")
ME = reverse("auth-me")


def register_payload(**overrides):
    data = {
        "full_name": "Tutora de Prueba",
        "email": "nueva@pettech.test",
        "password": DEFAULT_PASSWORD,
        "accept_privacy_policy": True,
    }
    data.update(overrides)
    return data


class RegisterTests(PetTechAPITestCase):
    def test_registro_crea_tutor_y_entrega_token(self):
        response = self.client.post(REGISTER, register_payload(), format="json")
        self.assertEqual(response.status_code, 201, response.content)
        body = response.json()
        self.assertTrue(body["token"].startswith("pt_"))
        self.assertEqual(body["user"]["role"], "TUTOR")
        self.assertNotIn("password", body["user"])
        user = User.objects.get(email="nueva@pettech.test")
        self.assertIsNotNone(user.privacy_policy_accepted_at)
        self.assertTrue(user.privacy_policy_version)

    def test_registro_exige_aceptar_politica_de_privacidad(self):
        response = self.client.post(REGISTER, register_payload(accept_privacy_policy=False), format="json")
        self.assertErrorCode(response, 400, "validation_error")
        self.assertIn("accept_privacy_policy", response.json()["error"]["fields"])
        self.assertFalse(User.objects.filter(email="nueva@pettech.test").exists())

    def test_registro_rechaza_correo_duplicado_sin_distinguir_mayusculas(self):
        self.make_user(email="duplicado@pettech.test")
        response = self.client.post(REGISTER, register_payload(email="DUPLICADO@PetTech.test"), format="json")
        self.assertErrorCode(response, 400, "validation_error")
        self.assertIn("email", response.json()["error"]["fields"])

    def test_registro_rechaza_contrasena_debil(self):
        for weak in ["123456789012", "password123", "corta"]:
            with self.subTest(password=weak):
                response = self.client.post(
                    REGISTER, register_payload(email=f"{len(weak)}@pettech.test", password=weak), format="json"
                )
                self.assertErrorCode(response, 400, "validation_error")
                self.assertIn("password", response.json()["error"]["fields"])

    def test_cliente_no_puede_elegir_rol_profesional_ni_admin(self):
        response = self.client.post(
            REGISTER, register_payload(role="PROFESSIONAL", is_staff=True, is_superuser=True), format="json"
        )
        self.assertEqual(response.status_code, 201)
        user = User.objects.get(email="nueva@pettech.test")
        self.assertEqual(user.role, User.Role.TUTOR)
        self.assertFalse(user.is_staff)
        self.assertFalse(user.is_superuser)

    def test_contrasena_almacenada_con_argon2(self):
        self.client.post(REGISTER, register_payload(), format="json")
        user = User.objects.get(email="nueva@pettech.test")
        self.assertTrue(user.password.startswith("argon2"))
        self.assertNotIn(DEFAULT_PASSWORD, user.password)

    def test_correo_se_normaliza_a_minusculas(self):
        self.client.post(REGISTER, register_payload(email="  Mixto@PetTech.TEST "), format="json")
        self.assertTrue(User.objects.filter(email="mixto@pettech.test").exists())


class LoginTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.user = self.make_user(email="tutor@pettech.test")

    def test_inicio_de_sesion_correcto(self):
        response = self.client.post(LOGIN, {"email": "TUTOR@pettech.test", "password": DEFAULT_PASSWORD}, format="json")
        self.assertEqual(response.status_code, 200, response.content)
        body = response.json()
        self.assertIn("token", body)
        self.assertIn("expiry", body)
        self.assertEqual(body["user"]["email"], "tutor@pettech.test")

    def test_mensaje_generico_para_clave_incorrecta_y_correo_inexistente(self):
        wrong_password = self.client.post(LOGIN, {"email": "tutor@pettech.test", "password": "x" * 12}, format="json")
        unknown_email = self.client.post(LOGIN, {"email": "nadie@pettech.test", "password": "x" * 12}, format="json")
        self.assertErrorCode(wrong_password, 400, "validation_error")
        self.assertErrorCode(unknown_email, 400, "validation_error")
        # Mismo mensaje: no permite enumerar cuentas existentes.
        self.assertEqual(wrong_password.json()["error"]["message"], unknown_email.json()["error"]["message"])

    def test_cuenta_suspendida_no_puede_iniciar_sesion(self):
        self.user.is_active = False
        self.user.save()
        response = self.client.post(LOGIN, {"email": "tutor@pettech.test", "password": DEFAULT_PASSWORD}, format="json")
        self.assertErrorCode(response, 400, "validation_error")

    def test_token_suspendido_deja_de_funcionar(self):
        self.authenticate(self.user)
        self.assertEqual(self.client.get(ME).status_code, 200)
        self.user.is_active = False
        self.user.save()
        self.assertErrorCode(self.client.get(ME), 401, "authentication_failed")

    def test_token_se_guarda_como_hash(self):
        token = self.authenticate(self.user)
        stored = AuthToken.objects.get(user=self.user)
        self.assertNotEqual(stored.digest, token)
        self.assertNotIn(token, stored.digest)

    def test_limite_de_intentos_de_inicio_de_sesion(self):
        """Protección contra fuerza bruta: el intento 11 dentro del minuto es rechazado (429)."""
        for _ in range(10):
            self.client.post(LOGIN, {"email": "tutor@pettech.test", "password": "incorrecta-123"}, format="json")
        response = self.client.post(LOGIN, {"email": "tutor@pettech.test", "password": DEFAULT_PASSWORD}, format="json")
        self.assertErrorCode(response, 429, "throttled")

    def test_duracion_de_sesion_menor_para_profesionales(self):
        tutor_token, _ = issue_token(self.user)
        pro = self.make_user(email="pro@pettech.test", role=User.Role.PROFESSIONAL)
        pro_token, _ = issue_token(pro)
        self.assertLess(pro_token.expiry, tutor_token.expiry)
        self.assertLessEqual(pro_token.expiry - timezone.now(), timedelta(hours=12, minutes=1))

    def test_limite_de_sesiones_simultaneas(self):
        for _ in range(8):
            issue_token(self.user)
        self.assertEqual(AuthToken.objects.filter(user=self.user).count(), 5)


class SessionTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.user = self.make_user(email="sesion@pettech.test")

    def test_me_requiere_autenticacion(self):
        self.assertErrorCode(self.client.get(ME), 401, "not_authenticated")

    def test_me_devuelve_datos_propios(self):
        self.authenticate(self.user)
        response = self.client.get(ME)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["email"], "sesion@pettech.test")

    def test_cierre_de_sesion_revoca_el_token(self):
        self.authenticate(self.user)
        self.assertEqual(self.client.post(LOGOUT).status_code, 204)
        self.assertErrorCode(self.client.get(ME), 401, "authentication_failed")

    def test_cierre_en_todos_los_dispositivos(self):
        _, other_device = issue_token(self.user)
        self.authenticate(self.user)
        self.assertEqual(self.client.post(LOGOUT_ALL).status_code, 204)
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {other_device}")
        self.assertErrorCode(self.client.get(ME), 401, "authentication_failed")

    def test_token_expirado_es_rechazado(self):
        self.authenticate(self.user)
        AuthToken.objects.filter(user=self.user).update(expiry=timezone.now() - timedelta(seconds=1))
        self.assertErrorCode(self.client.get(ME), 401, "authentication_failed")

    def test_token_inventado_es_rechazado(self):
        self.client.credentials(HTTP_AUTHORIZATION="Token pt_" + "a" * 64)
        self.assertErrorCode(self.client.get(ME), 401, "authentication_failed")
