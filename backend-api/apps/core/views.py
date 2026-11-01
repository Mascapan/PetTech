"""Vistas transversales: salud del servicio y manejadores de error JSON."""
import logging

from django.db import connection
from django.http import JsonResponse
from drf_spectacular.utils import extend_schema, inline_serializer
from rest_framework import serializers, status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .exceptions import error_payload

logger = logging.getLogger("pettech.health")


class HealthView(APIView):
    """
    Verificación de salud para monitoreo (NS-01) y para que la app compruebe la conexión.

    Confirma que la base de datos responde y que la extensión PostGIS está disponible.
    No expone versiones ni detalles internos.
    """

    permission_classes = [AllowAny]
    authentication_classes = []

    @extend_schema(
        responses=inline_serializer(
            "HealthResponse",
            fields={
                "status": serializers.CharField(),
                "database": serializers.CharField(),
                "postgis": serializers.CharField(),
            },
        )
    )
    def get(self, request):
        result = {"status": "ok", "database": "ok", "postgis": "ok"}
        http_status = status.HTTP_200_OK
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT 1")
                cursor.fetchone()
                cursor.execute("SELECT PostGIS_Lib_Version()")
                cursor.fetchone()
        except Exception:  # noqa: BLE001 — se informa estado degradado sin detalles
            logger.exception("Fallo en verificación de salud")
            result = {"status": "degraded", "database": "error", "postgis": "unknown"}
            http_status = status.HTTP_503_SERVICE_UNAVAILABLE
        return Response(result, status=http_status)


def json_not_found(request, exception=None):
    return JsonResponse(error_payload("not_found"), status=404)


def json_server_error(request):
    return JsonResponse(error_payload("server_error"), status=500)
