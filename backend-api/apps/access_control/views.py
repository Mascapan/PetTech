import uuid

from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import status
from rest_framework.generics import get_object_or_404
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.core.permissions import IsProfessional, IsTutor, IsTutorOrProfessional
from apps.pets.models import Pet

from . import services
from .models import AccessRequest, AuditEvent
from .serializers import AccessRequestCreateSerializer, AccessRequestSerializer, AuditEventSerializer


class AccessRequestListCreateView(APIView):
    """
    GET: tutor → solicitudes sobre sus mascotas; profesional → solicitudes que él realizó.
    POST (profesional): solicitar acceso ingresando el código PetTech (HU-16).
    """

    permission_classes = [IsTutorOrProfessional]

    def get_permissions(self):
        if self.request.method == "POST":
            return [IsProfessional()]
        return super().get_permissions()

    @extend_schema(tags=["access"], summary="Listar solicitudes de acceso",
                   parameters=[OpenApiParameter("status", str, description="PENDING, APPROVED, ...")],
                   responses={200: AccessRequestSerializer(many=True)})
    def get(self, request):
        qs = AccessRequest.objects.select_related("pet", "clinic", "professional")
        if request.user.role == "TUTOR":
            qs = qs.filter(pet__owner=request.user)
        else:
            qs = qs.filter(professional=request.user)
        pet_id = request.query_params.get("pet")
        if pet_id and request.user.role == "TUTOR":
            try:
                qs = qs.filter(pet_id=uuid.UUID(pet_id))
            except ValueError:
                qs = qs.none()
        wanted = request.query_params.get("status")
        items = [r for r in qs[:200] if not wanted or r.effective_status == wanted]
        return Response(AccessRequestSerializer(items, many=True, context={"request": request}).data)

    @extend_schema(tags=["access"], summary="Solicitar acceso con el código PetTech (profesional)",
                   request=AccessRequestCreateSerializer, responses={201: AccessRequestSerializer})
    def post(self, request):
        data = AccessRequestCreateSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        req = services.request_access(request.user, data.validated_data["public_code"],
                                      data.validated_data.get("reason", ""))
        return Response(AccessRequestSerializer(req, context={"request": request}).data,
                        status=status.HTTP_201_CREATED)


class _TutorDecisionView(APIView):
    permission_classes = [IsTutor]
    action_name = ""

    def run(self, request, pk):
        raise NotImplementedError

    @extend_schema(tags=["access"], request=None, responses={200: AccessRequestSerializer})
    def post(self, request, pk):
        req = self.run(request, pk)
        return Response(AccessRequestSerializer(req, context={"request": request}).data)


class ApproveView(_TutorDecisionView):
    """El tutor autoriza por 24 h (configurable) al establecimiento solicitante (HU-17, RN-12)."""

    def run(self, request, pk):
        return services.decide(request.user, pk, approve=True)


class RejectView(_TutorDecisionView):
    def run(self, request, pk):
        return services.decide(request.user, pk, approve=False)


class RevokeView(_TutorDecisionView):
    """Revocación con efecto inmediato (HU-18)."""

    def run(self, request, pk):
        return services.revoke(request.user, pk)


class PetAuditLogView(APIView):
    """El tutor ve quién consultó o modificó la ficha de su mascota (HU-24)."""

    permission_classes = [IsTutor]

    @extend_schema(tags=["access"], summary="Bitácora de accesos de una mascota",
                   responses={200: AuditEventSerializer(many=True)})
    def get(self, request, pet_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        events = AuditEvent.objects.filter(pet=pet).exclude(actor=request.user)[:100]
        return Response(AuditEventSerializer(events, many=True).data)
