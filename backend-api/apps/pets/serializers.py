from datetime import date

from django.utils import timezone
from rest_framework import serializers

from .models import Pet

MAX_AGE_YEARS = 40


class PetSerializer(serializers.ModelSerializer):
    """
    Perfil de mascota (HU-02) y datos del carnet digital.

    - El tutor NUNCA se recibe desde el cliente: se asigna a partir del token (perform_create).
    - id y public_code son de solo lectura e inmutables.
    """

    species_label = serializers.CharField(source="get_species_display", read_only=True)
    sex_label = serializers.CharField(source="get_sex_display", read_only=True)
    photo_id = serializers.SerializerMethodField(help_text="Foto de perfil en el servidor (null si no tiene).")
    data_origin = serializers.SerializerMethodField(
        help_text="Origen de los datos del perfil: siempre declarados por el tutor."
    )

    class Meta:
        model = Pet
        fields = [
            "id",
            "public_code",
            "name",
            "species",
            "species_label",
            "breed",
            "sex",
            "sex_label",
            "birth_date",
            "birth_date_is_estimated",
            "microchip_number",
            "general_notes",
            "data_origin",
            "photo_id",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "public_code", "created_at", "updated_at"]
        extra_kwargs = {
            "breed": {"required": False},
            "sex": {"required": False},
            "microchip_number": {"required": False},
            "general_notes": {"required": False},
        }

    def get_photo_id(self, obj) -> str | None:
        att = obj.attachments.filter(kind="PET_PHOTO").order_by("-created_at").values_list("pk", flat=True).first()
        return str(att) if att else None

    def get_data_origin(self, obj) -> str:
        return "OWNER_DECLARED"

    def validate_name(self, value: str) -> str:
        value = " ".join(value.split())
        if not value:
            raise serializers.ValidationError("El nombre de la mascota es obligatorio.")
        return value

    def validate_breed(self, value: str) -> str:
        return " ".join((value or "").split())

    def validate_microchip_number(self, value: str) -> str:
        return (value or "").replace(" ", "")

    def validate_birth_date(self, value: date | None) -> date | None:
        if value is None:
            return value
        today = timezone.localdate()  # fecha actual en America/Santiago
        if value > today:
            raise serializers.ValidationError("La fecha de nacimiento no puede ser posterior a hoy.")
        if value.year < today.year - MAX_AGE_YEARS:
            raise serializers.ValidationError(
                f"La fecha de nacimiento no puede ser anterior a {MAX_AGE_YEARS} años."
            )
        return value
