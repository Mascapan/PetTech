import logging

from drf_spectacular.utils import extend_schema, extend_schema_view
from django.db import transaction
from rest_framework import viewsets
from rest_framework.exceptions import NotFound, ValidationError
from rest_framework.generics import get_object_or_404
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.core.exceptions import Conflict
from apps.core.permissions import IsTutor

from apps.clinical_records.models import Attachment

from .models import Pet
from .serializers import PetSerializer

logger = logging.getLogger("pettech.pets")


@extend_schema_view(
    list=extend_schema(summary="Listar mis mascotas", tags=["pets"]),
    create=extend_schema(summary="Registrar una mascota", tags=["pets"]),
    retrieve=extend_schema(summary="Ver una mascota propia (carnet digital)", tags=["pets"]),
    update=extend_schema(summary="Reemplazar los datos de una mascota propia", tags=["pets"]),
    partial_update=extend_schema(summary="Editar una mascota propia", tags=["pets"]),
    destroy=extend_schema(summary="Eliminar una mascota propia", tags=["pets"]),
)
class PetViewSet(viewsets.ModelViewSet):
    """
    Mascotas del tutor autenticado (HU-02, RNF-02).

    Autorización a nivel de objeto: el queryset se filtra SIEMPRE por el tutor del token.
    Una mascota ajena responde 404 (no 403) para no revelar su existencia.
    """

    serializer_class = PetSerializer
    permission_classes = [IsAuthenticated, IsTutor]
    lookup_value_regex = "[0-9a-fA-F-]{36}"

    def get_queryset(self):
        return Pet.objects.filter(owner=self.request.user).order_by("name", "created_at")

    def perform_create(self, serializer):
        pet = serializer.save(owner=self.request.user)
        logger.info("Mascota creada pet_id=%s owner_id=%s", pet.pk, self.request.user.pk)

    def perform_update(self, serializer):
        pet = serializer.save()
        logger.info("Mascota actualizada pet_id=%s owner_id=%s", pet.pk, self.request.user.pk)

    def perform_destroy(self, instance):
        """
        Los registros de veterinarias no se eliminan en cascada (RN-06; DEC-009 pendiente):
        si existen, la eliminación se rechaza con un mensaje claro. Los antecedentes
        declarados por el tutor sí se eliminan junto con la mascota.
        """
        from apps.clinical_records.models import ClinicalEntry, Origin

        pet_id = instance.pk
        entries = ClinicalEntry.objects.filter(pet=instance)
        if entries.filter(origin=Origin.PROFESSIONAL).exists():
            raise Conflict(
                "Esta mascota tiene registros de veterinarias, que no pueden eliminarse desde la aplicación. "
                "Escríbenos para gestionar la solicitud."
            )
        with transaction.atomic():
            # Orden por las referencias protegidas: fotos de antecedentes → antecedentes → fotos sueltas.
            Attachment.objects.filter(pet=instance, entry__isnull=False).delete()
            # Primero las correcciones (dependen de la versión anterior), luego el resto.
            for entry in entries.filter(corrects__isnull=False).order_by("-created_at"):
                entry.delete()
            entries.all().delete()
            Attachment.objects.filter(pet=instance).delete()
            instance.delete()
        logger.info("Mascota eliminada pet_id=%s owner_id=%s", pet_id, self.request.user.pk)


class PetPhotoView(APIView):
    """
    Foto de perfil de la mascota guardada en el servidor (DEC-031): no se pierde al cambiar de teléfono y
    la ve el profesional autorizado. Se normaliza (JPEG, sin EXIF/GPS) igual que las fotos de la ficha.
    """

    permission_classes = [IsAuthenticated, IsTutor]
    parser_classes = [MultiPartParser, FormParser]

    def _pet(self, request, pk):
        return get_object_or_404(Pet, pk=pk, owner=request.user)

    @extend_schema(tags=["pets"], summary="Ver la foto de la mascota (JPEG)", responses={(200, "image/jpeg"): bytes})
    def get(self, request, pk):
        from apps.clinical_records.views import image_response

        att = photo_of(self._pet(request, pk))
        if att is None:
            raise NotFound("La mascota no tiene foto.")
        return image_response(att)

    @extend_schema(tags=["pets"], summary="Subir o reemplazar la foto de la mascota",
                   request={"multipart/form-data": {"type": "object", "properties": {"file": {"type": "string",
                                                                                              "format": "binary"}}}},
                   responses={200: dict})
    def put(self, request, pk):
        from apps.clinical_records import services as records

        pet = self._pet(request, pk)
        upload = request.FILES.get("file")
        if upload is None:
            raise ValidationError({"file": ["Adjunta una imagen."]})
        with transaction.atomic():
            previous = list(Attachment.objects.filter(pet=pet, kind=Attachment.Kind.PET_PHOTO))
            for att in previous:
                att.delete()
            att = records.add_attachment(request.user, pet, upload, "PET_PHOTO")
        return Response({"photo_id": str(att.pk)})

    post = put

    @extend_schema(tags=["pets"], summary="Quitar la foto de la mascota", responses={204: None})
    def delete(self, request, pk):
        Attachment.objects.filter(pet=self._pet(request, pk), kind=Attachment.Kind.PET_PHOTO).delete()
        return Response(status=204)



def photo_of(pet):
    """Foto de perfil vigente (sin cargar el contenido binario)."""
    return Attachment.objects.filter(pet=pet, kind=Attachment.Kind.PET_PHOTO).defer("content").order_by("-created_at").first()
