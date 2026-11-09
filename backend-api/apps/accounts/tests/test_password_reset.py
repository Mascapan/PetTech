"""Recuperación de acceso por correo (HU-01)."""
import re

from django.core import mail

from apps.accounts.models import PasswordResetCode
from apps.core.tests.helpers import DEFAULT_PASSWORD, PetTechAPITestCase

START = "/api/v1/auth/password-reset/"
CONFIRM = "/api/v1/auth/password-reset/confirm/"
NEW = "Nueva-Clave-Segura-2026"


class PasswordResetTests(PetTechAPITestCase):
    def setUp(self):
        super().setUp()
        self.user = self.make_user("tutor@pettech.test")

    def code_from_mail(self):
        return re.search(r"\b(\d{6})\b", mail.outbox[-1].body).group(1)

    def test_respuesta_identica_exista_o_no_el_correo(self):
        a = self.client.post(START, {"email": "tutor@pettech.test"})
        b = self.client.post(START, {"email": "nadie@pettech.test"})
        self.assertEqual((a.status_code, a.json()), (b.status_code, b.json()))
        self.assertEqual(len(mail.outbox), 1)

    def test_codigo_valido_cambia_la_clave_y_cierra_sesiones(self):
        token = self.authenticate(self.user)
        self.client.post(START, {"email": "tutor@pettech.test"})
        self.logout_client()
        r = self.client.post(CONFIRM, {"email": "tutor@pettech.test", "code": self.code_from_mail(),
                                       "new_password": NEW})
        self.assertEqual(r.status_code, 200, r.content)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password(NEW))
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {token}")
        self.assertEqual(self.client.get("/api/v1/auth/me/").status_code, 401)
        # El código es de un solo uso.
        self.logout_client()
        r = self.client.post(CONFIRM, {"email": "tutor@pettech.test", "code": self.code_from_mail(),
                                       "new_password": "Otra-Clave-Segura-2026"})
        self.assertEqual(r.status_code, 400)

    def test_codigo_incorrecto_y_limite_de_intentos(self):
        self.client.post(START, {"email": "tutor@pettech.test"})
        good = self.code_from_mail()
        bad = "000000" if good != "000000" else "111111"
        for _ in range(PasswordResetCode.MAX_ATTEMPTS):
            self.client.post(CONFIRM, {"email": "tutor@pettech.test", "code": bad, "new_password": NEW})
        r = self.client.post(CONFIRM, {"email": "tutor@pettech.test", "code": good, "new_password": NEW})
        self.assertEqual(r.status_code, 400)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password(DEFAULT_PASSWORD))

    def test_clave_nueva_debil_rechazada(self):
        self.client.post(START, {"email": "tutor@pettech.test"})
        r = self.client.post(CONFIRM, {"email": "tutor@pettech.test", "code": self.code_from_mail(),
                                       "new_password": "123"})
        self.assertIn("new_password", r.json()["error"]["fields"])


class AccountDeletionRequestTests(PetTechAPITestCase):
    def test_solicitud_registrada_y_sesiones_cerradas(self):
        from apps.accounts.models import AccountDeletionRequest

        user = self.make_user("borrar@pettech.test")
        token = self.authenticate(user)
        r = self.client.post("/api/v1/auth/account-deletion/", {"reason": "Ya no la uso"})
        self.assertEqual(r.status_code, 201)
        self.assertEqual(AccountDeletionRequest.objects.get().email, "borrar@pettech.test")
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {token}")
        self.assertEqual(self.client.get("/api/v1/auth/me/").status_code, 401)
