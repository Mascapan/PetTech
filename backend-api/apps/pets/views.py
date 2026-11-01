import logging

from drf_spectacular.utils import extend_schema, extend_schema_view
from rest_framework import viewsets
from rest_framework.permissions import IsAuthenticated

from apps.core.permissions import IsTutor

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
        pet_id = instance.pk
        instance.delete()
        logger.info("Mascota eliminada pet_id=%s owner_id=%s", pet_id, self.request.user.pk)
