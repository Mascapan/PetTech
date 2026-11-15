from django.http import HttpResponse
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import status
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.generics import get_object_or_404
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.access_control import services as access
from apps.access_control.models import AuditEvent
from apps.core.permissions import IsProfessional, IsTutor
from apps.pets.models import Pet

from . import services
from .models import Attachment, EntryType
from .serializers import (
    AttachmentSerializer,
    AttachmentUploadSerializer,
    ClinicalEntrySerializer,
    CorrectionSerializer,
    build_record_payload,
)

TYPE_PARAM = OpenApiParameter("type", str, description=f"Filtra por tipo: {', '.join(EntryType.values)}")


def _type_filter(request):
    value = request.query_params.get("type")
    return value if value in EntryType.values else None


def _split_correction(serializer):
    data = dict(serializer.validated_data)
    reason = data.pop("reason")
    return data, reason


# ------------------------------------------------------------------ Tutor (titular)

class OwnerRecordView(APIView):
    """Ficha clínica de una mascota propia: antecedentes declarados y registros de veterinarias (HU-04, HU-23)."""

    permission_classes = [IsTutor]

    @extend_schema(tags=["clinical-records"], summary="Ficha clínica (tutor)", parameters=[TYPE_PARAM],
                   responses={200: dict})
    def get(self, request, pet_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        return Response(build_record_payload(pet, services.current_entries(pet, _type_filter(request))))


class OwnerEntryCreateView(APIView):
    permission_classes = [IsTutor]

    @extend_schema(tags=["clinical-records"], summary="Registrar antecedente declarado (HU-03)",
                   request=ClinicalEntrySerializer, responses={201: ClinicalEntrySerializer})
    def post(self, request, pet_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        serializer = ClinicalEntrySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        entry = services.create_by_owner(request.user, pet, serializer.validated_data)
        return Response(ClinicalEntrySerializer(entry).data, status=status.HTTP_201_CREATED)


class OwnerEntryCorrectView(APIView):
    permission_classes = [IsTutor]

    @extend_schema(tags=["clinical-records"], summary="Corregir un antecedente declarado",
                   request=CorrectionSerializer, responses={201: ClinicalEntrySerializer})
    def post(self, request, pet_id, entry_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        serializer = CorrectionSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data, reason = _split_correction(serializer)
        entry = services.correct(request.user, pet, entry_id, data, reason)
        return Response(ClinicalEntrySerializer(entry).data, status=status.HTTP_201_CREATED)


class OwnerEntryHistoryView(APIView):
    permission_classes = [IsTutor]

    @extend_schema(tags=["clinical-records"], summary="Versiones de un antecedente",
                   responses={200: ClinicalEntrySerializer(many=True)})
    def get(self, request, pet_id, entry_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        return Response(ClinicalEntrySerializer(services.history(pet, entry_id), many=True).data)


# ------------------------------------------------------------------ Profesional autorizado

class ProfessionalProfileView(APIView):
    """Establecimiento verificado al que pertenece el profesional."""

    permission_classes = [IsProfessional]

    @extend_schema(tags=["professional"], summary="Mi establecimiento", responses={200: dict})
    def get(self, request):
        membership = access.membership_for(request.user)
        return Response({
            "full_name": request.user.full_name,
            "clinic_id": str(membership.clinic_id),
            "clinic_name": membership.clinic.name,
            "license_number": membership.license_number,
        })


class ProfessionalRecordView(APIView):
    """
    Ficha clínica para un profesional con autorización VIGENTE del tutor (HU-19).
    Se verifica en cada consulta y cada lectura queda en la bitácora (RNF-09, RNF-10).
    """

    permission_classes = [IsProfessional]

    @extend_schema(tags=["professional"], summary="Ficha clínica por código PetTech", parameters=[TYPE_PARAM],
                   responses={200: dict})
    def get(self, request, code):
        pet, membership = access.require_active_grant(request.user, code, AuditEvent.Action.RECORD_READ)
        access.audit(request.user, AuditEvent.Action.RECORD_READ, pet=pet, clinic=membership.clinic)
        entries = services.current_entries(pet, _type_filter(request))
        return Response(build_record_payload(pet, entries, include_pet_id=False))


class ProfessionalEntryCreateView(APIView):
    permission_classes = [IsProfessional]

    @extend_schema(tags=["professional"], summary="Registrar atención, vacuna u otro antecedente (HU-20, HU-21)",
                   request=ClinicalEntrySerializer, responses={201: ClinicalEntrySerializer})
    def post(self, request, code):
        pet, membership = access.require_active_grant(request.user, code, AuditEvent.Action.RECORD_CREATED)
        serializer = ClinicalEntrySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        entry = services.create_by_professional(request.user, membership, pet, serializer.validated_data)
        return Response(ClinicalEntrySerializer(entry).data, status=status.HTTP_201_CREATED)


class ProfessionalEntryCorrectView(APIView):
    permission_classes = [IsProfessional]

    @extend_schema(tags=["professional"], summary="Corregir un registro del propio establecimiento (HU-22)",
                   request=CorrectionSerializer, responses={201: ClinicalEntrySerializer})
    def post(self, request, code, entry_id):
        pet, membership = access.require_active_grant(request.user, code, AuditEvent.Action.RECORD_CORRECTED)
        serializer = CorrectionSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data, reason = _split_correction(serializer)
        entry = services.correct(request.user, pet, entry_id, data, reason, membership=membership)
        return Response(ClinicalEntrySerializer(entry).data, status=status.HTTP_201_CREATED)


# ------------------------------------------------------------------ Fotografías (DEC-031)

def image_response(att: Attachment) -> HttpResponse:
    response = HttpResponse(bytes(att.content), content_type=att.content_type)
    response["Cache-Control"] = "private, max-age=3600"
    response["Content-Disposition"] = f'inline; filename="pettech-{att.pk}.jpg"'
    return response


class OwnerAttachmentCreateView(APIView):
    permission_classes = [IsTutor]
    parser_classes = [MultiPartParser, FormParser]

    @extend_schema(tags=["clinical-records"], summary="Adjuntar foto (etiqueta de vacuna o carnet)",
                   request={"multipart/form-data": AttachmentUploadSerializer}, responses={201: AttachmentSerializer})
    def post(self, request, pet_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        form = AttachmentUploadSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        att = services.add_attachment(request.user, pet, form.validated_data["file"], form.validated_data["kind"],
                                      entry_id=form.validated_data.get("entry"))
        return Response(AttachmentSerializer(att).data, status=status.HTTP_201_CREATED)


class OwnerAttachmentView(APIView):
    permission_classes = [IsTutor]

    @extend_schema(tags=["clinical-records"], summary="Ver la foto (JPEG)", responses={(200, "image/jpeg"): bytes})
    def get(self, request, pet_id, attachment_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        return image_response(get_object_or_404(Attachment, pk=attachment_id, pet=pet))

    @extend_schema(tags=["clinical-records"], summary="Eliminar una foto propia", responses={204: None})
    def delete(self, request, pet_id, attachment_id):
        pet = get_object_or_404(Pet, pk=pet_id, owner=request.user)
        services.delete_own_attachment(request.user, pet, attachment_id)
        return Response(status=status.HTTP_204_NO_CONTENT)


class ProfessionalAttachmentCreateView(APIView):
    permission_classes = [IsProfessional]
    parser_classes = [MultiPartParser, FormParser]

    @extend_schema(tags=["professional"], summary="Adjuntar foto de la etiqueta a un registro propio",
                   request={"multipart/form-data": AttachmentUploadSerializer}, responses={201: AttachmentSerializer})
    def post(self, request, code):
        pet, membership = access.require_active_grant(request.user, code, AuditEvent.Action.ATTACHMENT_ADDED)
        form = AttachmentUploadSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        att = services.add_attachment(request.user, pet, form.validated_data["file"], form.validated_data["kind"],
                                      entry_id=form.validated_data.get("entry"), membership=membership)
        return Response(AttachmentSerializer(att).data, status=status.HTTP_201_CREATED)


class ProfessionalAttachmentView(APIView):
    permission_classes = [IsProfessional]

    @extend_schema(tags=["professional"], summary="Ver una foto de la ficha (JPEG)",
                   responses={(200, "image/jpeg"): bytes})
    def get(self, request, code, attachment_id):
        pet, membership = access.require_active_grant(request.user, code, AuditEvent.Action.ATTACHMENT_READ)
        att = get_object_or_404(Attachment, pk=attachment_id, pet=pet)
        access.audit(request.user, AuditEvent.Action.ATTACHMENT_READ, pet=pet, clinic=membership.clinic,
                     resource_id=att.pk)
        return image_response(att)
