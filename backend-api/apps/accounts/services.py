"""
Servicios de autenticación (lógica de negocio separada de las vistas).

Los tokens se emiten con django-rest-knox: el valor completo se entrega una sola vez al
cliente y en la base de datos solo se guarda su hash (SHA-512). Cada token tiene
expiración según el rol y puede revocarse individualmente (cierre de sesión).
"""
import logging
import secrets
from datetime import timedelta

from django.conf import settings
from django.contrib.auth.hashers import check_password, make_password
from django.contrib.auth.signals import user_logged_in
from django.core.mail import send_mail
from django.db import transaction
from django.utils import timezone
from knox.models import AuthToken

from .models import PasswordResetCode, User

logger = logging.getLogger("pettech.auth")


def token_ttl_for(user: User):
    return settings.PETTECH_TOKEN_TTL.get(user.role, settings.PETTECH_TOKEN_TTL["TUTOR"])


@transaction.atomic
def issue_token(user: User, request=None) -> tuple[AuthToken, str]:
    """Emite un token nuevo y aplica el límite de sesiones simultáneas por usuario."""
    limit = settings.PETTECH_MAX_TOKENS_PER_USER
    existing = AuthToken.objects.filter(user=user).order_by("-created")
    stale_ids = list(existing.values_list("digest", flat=True)[max(limit - 1, 0):])
    if stale_ids:
        AuthToken.objects.filter(digest__in=stale_ids).delete()

    instance, token = AuthToken.objects.create(user=user, expiry=token_ttl_for(user))
    user_logged_in.send(sender=user.__class__, request=request, user=user)
    logger.info("Sesión iniciada user_id=%s role=%s", user.pk, user.role)
    return instance, token


def revoke_token(auth_token: AuthToken) -> None:
    user_id = auth_token.user_id
    auth_token.delete()
    logger.info("Sesión cerrada user_id=%s", user_id)


def revoke_all_tokens(user: User) -> int:
    deleted, _ = AuthToken.objects.filter(user=user).delete()
    logger.info("Todas las sesiones cerradas user_id=%s cantidad=%s", user.pk, deleted)
    return deleted


# ---------------------------------------------------------------- Recuperación de contraseña (HU-01)


def start_password_reset(email: str) -> None:
    """Envía un código de 6 dígitos si la cuenta existe. La respuesta al cliente es siempre la misma."""
    user = User.objects.filter(email__iexact=(email or "").strip().lower(), is_active=True).first()
    if user is None:
        logger.info("Recuperación solicitada para un correo no registrado")
        return
    PasswordResetCode.objects.filter(user=user, used_at__isnull=True).update(used_at=timezone.now())
    code = f"{secrets.randbelow(1_000_000):06d}"
    minutes = settings.PETTECH_PASSWORD_RESET_MINUTES
    PasswordResetCode.objects.create(
        user=user, code_hash=make_password(code), expires_at=timezone.now() + timedelta(minutes=minutes)
    )
    send_mail(
        subject="PetTech: código para recuperar tu contraseña",
        message=(
            f"Hola {user.full_name}:\n\nTu código para crear una nueva contraseña es: {code}\n"
            f"Vence en {minutes} minutos. Si no lo solicitaste, ignora este mensaje."
        ),
        from_email=None,
        recipient_list=[user.email],
    )
    logger.info("Código de recuperación emitido user_id=%s", user.pk)


@transaction.atomic
def confirm_password_reset(email: str, code: str, new_password: str) -> bool:
    user = User.objects.filter(email__iexact=(email or "").strip().lower(), is_active=True).first()
    if user is None:
        return False
    reset = (
        PasswordResetCode.objects.select_for_update()
        .filter(user=user, used_at__isnull=True, expires_at__gt=timezone.now())
        .first()
    )
    if reset is None or reset.attempts >= PasswordResetCode.MAX_ATTEMPTS:
        return False
    if not check_password(code, reset.code_hash):
        reset.attempts += 1
        reset.save(update_fields=["attempts"])
        return False
    reset.used_at = timezone.now()
    reset.save(update_fields=["used_at"])
    user.set_password(new_password)
    user.save(update_fields=["password"])
    revoke_all_tokens(user)  # cierra las sesiones abiertas con la contraseña anterior
    return True
