"""
Servicios de autenticación (lógica de negocio separada de las vistas).

Los tokens se emiten con django-rest-knox: el valor completo se entrega una sola vez al
cliente y en la base de datos solo se guarda su hash (SHA-512). Cada token tiene
expiración según el rol y puede revocarse individualmente (cierre de sesión).
"""
import logging

from django.conf import settings
from django.contrib.auth.signals import user_logged_in
from django.db import transaction
from knox.models import AuthToken

from .models import User

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
