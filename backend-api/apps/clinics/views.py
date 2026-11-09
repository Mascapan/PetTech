from drf_spectacular.utils import extend_schema
from rest_framework import generics
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from . import services
from .models import Clinic, Service
from .serializers import ClinicSearchQuerySerializer, ClinicSerializer


class ClinicSearchView(APIView):
    """
    Búsqueda de establecimientos cercanos con filtros combinables (HU-07, HU-08).
    Orden: abiertos según horario declarado primero; luego por distancia.
    """

    permission_classes = [IsAuthenticated]

    @extend_schema(
        tags=["clinics"],
        summary="Buscar veterinarias (cercanía y filtros)",
        parameters=[ClinicSearchQuerySerializer],
        responses={200: ClinicSerializer(many=True)},
    )
    def get(self, request):
        query = ClinicSearchQuerySerializer(data=request.query_params)
        query.is_valid(raise_exception=True)
        results = services.search(services.SearchParams(**query.validated_data))
        return Response({
            "count": len(results),
            "results": ClinicSerializer(results, many=True).data,
            "disclaimer": (
                "\"Abierto\" se calcula con el horario declarado por cada establecimiento; "
                "no confirma que esté atendiendo ni la disponibilidad de horas."
            ),
        })


@extend_schema(tags=["clinics"], summary="Ficha de un establecimiento (HU-09)")
class ClinicDetailView(generics.RetrieveAPIView):
    permission_classes = [IsAuthenticated]
    serializer_class = ClinicSerializer
    queryset = Clinic.objects.filter(is_active=True).prefetch_related("opening_hours")
    lookup_value_regex = "[0-9a-fA-F-]{36}"


class ServiceListView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=["clinics"], summary="Servicios disponibles para filtrar", responses={200: dict})
    def get(self, request):
        return Response([{"code": c, "label": label} for c, label in Service.choices])
