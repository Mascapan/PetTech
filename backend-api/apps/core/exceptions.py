"""
Manejo uniforme de errores de la API.

Formato de toda respuesta de error:
    {"error": {"code": "<código>", "message": "<mensaje para el usuario>", "fields": {...}}}

- "fields" solo aparece en errores de validación (400) y contiene los mensajes por campo.
- Los errores no controlados devuelven 500 con un mensaje genérico; el detalle técnico
  queda solo en el registro del servidor (no se filtra información interna al cliente).
"""
import logging

from django.core.exceptions import PermissionDenied as DjangoPermissionDenied
from django.http import Http404
from rest_framework import exceptions, status
from rest_framework.response import Response
from rest_framework.views import exception_handler

logger = logging.getLogger("pettech.errors")

_MESSAGES = {
    "validation_error": "Revisa los datos ingresados.",
    "not_authenticated": "Debes iniciar sesión para continuar.",
    "authentication_failed": "Tu sesión no es válida o expiró. Inicia sesión nuevamente.",
    "permission_denied": "No tienes permiso para realizar esta acción.",
    "not_found": "El recurso solicitado no existe o no tienes acceso a él.",
    "method_not_allowed": "Operación no permitida.",
    "throttled": "Demasiados intentos. Espera un momento antes de volver a intentarlo.",
    "unsupported_media_type": "Formato de solicitud no soportado. Usa JSON.",
    "parse_error": "La solicitud no tiene un formato JSON válido.",
    "server_error": "Ocurrió un error inesperado. Intenta nuevamente más tarde.",
}


def _flatten(detail):
    """Convierte ErrorDetail anidados en listas de textos simples."""
    if isinstance(detail, dict):
        return {key: _flatten(value) for key, value in detail.items()}
    if isinstance(detail, list):
        flat = []
        for item in detail:
            value = _flatten(item)
            if isinstance(value, list):
                flat.extend(value)
            else:
                flat.append(value)
        return flat
    return [str(detail)]


def error_payload(code: str, message: str | None = None, fields: dict | None = None) -> dict:
    body = {"code": code, "message": message or _MESSAGES.get(code, _MESSAGES["server_error"])}
    if fields:
        body["fields"] = fields
    return {"error": body}


def api_exception_handler(exc, context):
    # Normaliza excepciones de Django a las de DRF.
    if isinstance(exc, Http404):
        exc = exceptions.NotFound()
    elif isinstance(exc, DjangoPermissionDenied):
        exc = exceptions.PermissionDenied()

    response = exception_handler(exc, context)

    if response is None:
        # Error no controlado: se registra con traza y se responde de forma genérica.
        view = context.get("view")
        logger.exception("Error no controlado en %s", view.__class__.__name__ if view else "vista desconocida")
        return Response(error_payload("server_error"), status=status.HTTP_500_INTERNAL_SERVER_ERROR)

    if isinstance(exc, exceptions.ValidationError):
        detail = exc.detail
        fields = _flatten(detail) if isinstance(detail, dict) else {"non_field_errors": _flatten(detail)}
        # Mensaje principal: primer error no asociado a campo, si existe.
        non_field = fields.get("non_field_errors") if isinstance(fields, dict) else None
        message = non_field[0] if non_field else _MESSAGES["validation_error"]
        response.data = error_payload("validation_error", message, fields)
        return response

    code = getattr(exc, "default_code", "error")
    if isinstance(exc, exceptions.Throttled):
        code = "throttled"
    message = _MESSAGES.get(code)
    if message is None:
        message = str(getattr(exc, "detail", "")) or _MESSAGES["server_error"]
    # Los mensajes de permisos propios (p. ej. IsTutor.message) son seguros de mostrar.
    if code == "permission_denied" and getattr(exc, "detail", None):
        detail_text = str(exc.detail)
        if detail_text and detail_text != str(exceptions.PermissionDenied.default_detail):
            message = detail_text
    response.data = error_payload(code, message)
    return response
