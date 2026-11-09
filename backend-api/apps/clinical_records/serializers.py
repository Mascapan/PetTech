from django.utils import timezone
from rest_framework import serializers

from apps.pets.models import Pet

from .models import ClinicalEntry, EntryType

TYPES_WITH_DUE_DATE = {EntryType.VACCINE, EntryType.DEWORMING, EntryType.VISIT, EntryType.MEDICATION}


class ClinicalEntrySerializer(serializers.ModelSerializer):
    entry_type_label = serializers.CharField(source="get_entry_type_display", read_only=True)
    origin_label = serializers.CharField(source="get_origin_display", read_only=True)
    is_correction = serializers.SerializerMethodField()

    class Meta:
        model = ClinicalEntry
        fields = [
            "id", "entry_type", "entry_type_label", "title", "occurred_on", "next_due_on", "notes",
            "weight_kg", "lot_number", "is_active", "origin", "origin_label", "author_name", "clinic_name",
            "created_at", "is_correction", "corrects", "correction_reason",
        ]
        read_only_fields = [
            "id", "origin", "author_name", "clinic_name", "created_at", "corrects", "correction_reason",
        ]
        extra_kwargs = {
            "notes": {"required": False},
            "lot_number": {"required": False},
            "is_active": {"required": False},
            "weight_kg": {"required": False, "coerce_to_string": False},
        }

    def get_is_correction(self, obj) -> bool:
        return obj.corrects_id is not None

    def validate_title(self, value):
        value = " ".join(value.split())
        if not value:
            raise serializers.ValidationError("Este campo es obligatorio.")
        return value

    def validate_occurred_on(self, value):
        # HU-03: no se aceptan fechas futuras para eventos realizados.
        if value > timezone.localdate():
            raise serializers.ValidationError("La fecha no puede ser posterior a hoy.")
        return value

    def validate(self, attrs):
        entry_type = attrs.get("entry_type")
        next_due = attrs.get("next_due_on")
        if next_due and entry_type not in TYPES_WITH_DUE_DATE:
            attrs["next_due_on"] = None
        elif next_due and next_due < attrs["occurred_on"]:
            raise serializers.ValidationError({"next_due_on": ["La próxima fecha debe ser posterior a la fecha."]})
        if entry_type == EntryType.WEIGHT and attrs.get("weight_kg") is None:
            raise serializers.ValidationError({"weight_kg": ["Indica el peso en kilogramos."]})
        return attrs


class CorrectionSerializer(ClinicalEntrySerializer):
    reason = serializers.CharField(max_length=200, write_only=True)

    class Meta(ClinicalEntrySerializer.Meta):
        fields = ClinicalEntrySerializer.Meta.fields + ["reason"]

    def validate_reason(self, value):
        value = value.strip()
        if len(value) < 5:
            raise serializers.ValidationError("Explica brevemente el motivo de la corrección.")
        return value


class PetSummarySerializer(serializers.ModelSerializer):
    species_label = serializers.CharField(source="get_species_display", read_only=True)
    sex_label = serializers.CharField(source="get_sex_display", read_only=True)

    class Meta:
        model = Pet
        fields = ["id", "public_code", "name", "species", "species_label", "breed", "sex", "sex_label",
                  "birth_date", "birth_date_is_estimated", "microchip_number", "general_notes"]


def build_record_payload(pet: Pet, entries, *, include_pet_id: bool = True) -> dict:
    """Ficha clínica completa: datos de la mascota, resumen de variables críticas y antecedentes vigentes."""
    data = ClinicalEntrySerializer(entries, many=True).data
    today = timezone.localdate()
    weights = [e for e in entries if e.weight_kg is not None]
    summary = {
        "latest_weight_kg": float(weights[0].weight_kg) if weights else None,
        "latest_weight_on": weights[0].occurred_on.isoformat() if weights else None,
        "allergies": [e.title for e in entries if e.entry_type == EntryType.ALLERGY and e.is_active],
        "chronic_conditions": [e.title for e in entries if e.entry_type == EntryType.CHRONIC_CONDITION and e.is_active],
        "active_medications": [e.title for e in entries if e.entry_type == EntryType.MEDICATION and e.is_active],
        "upcoming": sorted(
            [
                {"entry_type": e.entry_type, "title": e.title, "due_on": e.next_due_on.isoformat(),
                 "overdue": e.next_due_on < today}
                for e in entries if e.next_due_on
            ],
            key=lambda item: item["due_on"],
        ),
    }
    pet_data = PetSummarySerializer(pet).data
    if not include_pet_id:
        pet_data.pop("id")
    return {"pet": pet_data, "summary": summary, "entries": data}
