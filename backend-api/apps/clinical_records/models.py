"""
Ficha clínica estandarizada de la mascota.

Responde a la retroalimentación del profesor (28-10-2026): variables clínicas críticas
(alergias, patologías crónicas, peso, cirugías y fármacos activos) además de vacunas,
desparasitaciones y visitas, bajo un esquema único y tipado (entry_type).

Integridad (RN-05, RN-06, RNF-11):
- Cada antecedente conserva su ORIGEN (declarado por el tutor / registro profesional),
  autor, establecimiento y fecha.
- Los antecedentes NO se modifican ni se eliminan: una corrección crea una nueva versión
  enlazada a la anterior, con autor, fecha y motivo.
"""
import uuid
from decimal import Decimal

from django.conf import settings
from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models


class EntryType(models.TextChoices):
    VACCINE = "VACCINE", "Vacuna"
    DEWORMING = "DEWORMING", "Desparasitación"
    VISIT = "VISIT", "Visita / control"
    ALLERGY = "ALLERGY", "Alergia"
    CHRONIC_CONDITION = "CHRONIC_CONDITION", "Patología crónica"
    SURGERY = "SURGERY", "Cirugía"
    MEDICATION = "MEDICATION", "Fármaco"
    WEIGHT = "WEIGHT", "Peso"


class MucousMembranes(models.TextChoices):
    PINK = "PINK", "Rosadas (normales)"
    PALE = "PALE", "Pálidas"
    CYANOTIC = "CYANOTIC", "Cianóticas"
    ICTERIC = "ICTERIC", "Ictéricas"
    CONGESTED = "CONGESTED", "Congestivas"


class Route(models.TextChoices):
    SC = "SC", "Subcutánea"
    IM = "IM", "Intramuscular"
    ORAL = "ORAL", "Oral"
    INTRANASAL = "INTRANASAL", "Intranasal"
    TOPICAL = "TOPICAL", "Tópica"
    IV = "IV", "Intravenosa"


class Origin(models.TextChoices):
    OWNER_DECLARED = "OWNER_DECLARED", "Declarado por el tutor"
    PROFESSIONAL = "PROFESSIONAL", "Registro profesional"


class ClinicalEntry(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    pet = models.ForeignKey("pets.Pet", related_name="clinical_entries", on_delete=models.PROTECT)
    entry_type = models.CharField("tipo", max_length=20, choices=EntryType.choices)
    title = models.CharField(
        "descripción", max_length=120,
        help_text="Vacuna o producto aplicado, motivo de la visita, alergia, patología, fármaco o cirugía.",
    )
    occurred_on = models.DateField("fecha")
    next_due_on = models.DateField("próxima fecha", null=True, blank=True)
    notes = models.TextField("observaciones", max_length=2000, blank=True)
    weight_kg = models.DecimalField(
        "peso (kg)", max_digits=5, decimal_places=2, null=True, blank=True,
        validators=[MinValueValidator(Decimal("0.05")), MaxValueValidator(Decimal("150"))],
    )
    lot_number = models.CharField("lote / serie", max_length=40, blank=True)
    # --- Ficha estructurada (formato SOAP + signos vitales). Todos opcionales: se completan según el tipo.
    anamnesis = models.TextField("anamnesis (S)", max_length=2000, blank=True)
    temperature_c = models.DecimalField(
        "temperatura (°C)", max_digits=4, decimal_places=1, null=True, blank=True,
        validators=[MinValueValidator(Decimal("30")), MaxValueValidator(Decimal("45"))],
    )
    heart_rate_bpm = models.PositiveSmallIntegerField(
        "frecuencia cardíaca (lpm)", null=True, blank=True, validators=[MinValueValidator(20), MaxValueValidator(400)],
    )
    respiratory_rate_rpm = models.PositiveSmallIntegerField(
        "frecuencia respiratoria (rpm)", null=True, blank=True, validators=[MinValueValidator(4), MaxValueValidator(200)],
    )
    body_condition_score = models.PositiveSmallIntegerField(
        "condición corporal (1-9)", null=True, blank=True, validators=[MinValueValidator(1), MaxValueValidator(9)],
    )
    mucous_membranes = models.CharField("mucosas", max_length=10, choices=MucousMembranes.choices, blank=True)
    physical_exam = models.TextField("examen físico (O)", max_length=2000, blank=True)
    diagnosis = models.TextField("diagnóstico (A)", max_length=1000, blank=True)
    treatment = models.TextField("tratamiento / plan (P)", max_length=2000, blank=True)
    # --- Vacunas y fármacos
    manufacturer = models.CharField("laboratorio", max_length=80, blank=True)
    lot_expires_on = models.DateField("vencimiento del lote", null=True, blank=True)
    route = models.CharField("vía de administración", max_length=12, choices=Route.choices, blank=True)
    dose = models.CharField("dosis y frecuencia", max_length=120, blank=True)
    is_active = models.BooleanField(
        "vigente", default=True, help_text="Para alergias, patologías y fármacos: si sigue vigente."
    )

    origin = models.CharField("origen", max_length=20, choices=Origin.choices)
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="+")
    author_name = models.CharField("autor", max_length=150)
    clinic = models.ForeignKey("clinics.Clinic", null=True, blank=True, on_delete=models.PROTECT, related_name="+")
    clinic_name = models.CharField("establecimiento", max_length=120, blank=True)
    created_at = models.DateTimeField("registrado el", auto_now_add=True)

    corrects = models.OneToOneField(
        "self", null=True, blank=True, on_delete=models.PROTECT, related_name="corrected_by",
        help_text="Versión anterior que esta entrada corrige.",
    )
    correction_reason = models.CharField("motivo de la corrección", max_length=200, blank=True)
    source_attachment = models.ForeignKey(
        "clinical_records.Attachment", null=True, blank=True, on_delete=models.PROTECT, related_name="derived_entries",
        help_text="Foto del carnet de la que el tutor transcribió este antecedente (OCR con confirmación).",
    )

    class Meta:
        verbose_name = "antecedente clínico"
        verbose_name_plural = "antecedentes clínicos"
        ordering = ["-occurred_on", "-created_at"]
        indexes = [models.Index(fields=["pet", "entry_type"], name="clinical_pet_type_idx")]
        constraints = [
            models.CheckConstraint(
                condition=models.Q(next_due_on__isnull=True) | models.Q(next_due_on__gte=models.F("occurred_on")),
                name="clinical_next_after_occurred",
            ),
        ]

    def __str__(self) -> str:
        return f"{self.get_entry_type_display()}: {self.title} ({self.occurred_on})"

    def save(self, *args, **kwargs):
        if not self._state.adding:
            raise ValueError("Los antecedentes clínicos no se modifican: registra una corrección.")
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):
        if self.origin == Origin.PROFESSIONAL:
            raise ValueError("Los registros profesionales no se eliminan.")
        return super().delete(*args, **kwargs)


class Attachment(models.Model):
    """
    Fotografía asociada a la ficha (DEC-031): etiqueta de la vacuna aplicada o foto del carnet en papel.

    - Se guarda en PostgreSQL (BinaryField) re-codificada a JPEG, sin metadatos EXIF (puede contener GPS),
      con tamaño acotado: evita depender del disco efímero del hosting y hereda los mismos permisos que la ficha.
    - Es evidencia: no se modifica. El tutor solo puede eliminar las fotos que él mismo subió.
    """

    class Kind(models.TextChoices):
        VACCINE_LABEL = "VACCINE_LABEL", "Etiqueta de vacuna o producto"
        CARD_SCAN = "CARD_SCAN", "Foto del carnet"
        PET_PHOTO = "PET_PHOTO", "Foto de perfil de la mascota"
        OTHER = "OTHER", "Otro respaldo"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    pet = models.ForeignKey("pets.Pet", related_name="attachments", on_delete=models.PROTECT)
    entry = models.ForeignKey(ClinicalEntry, null=True, blank=True, related_name="attachments",
                              on_delete=models.PROTECT)
    kind = models.CharField("tipo", max_length=16, choices=Kind.choices)
    content = models.BinaryField("imagen (JPEG)")
    content_type = models.CharField(max_length=40, default="image/jpeg")
    size_bytes = models.PositiveIntegerField()
    width = models.PositiveSmallIntegerField()
    height = models.PositiveSmallIntegerField()
    sha256 = models.CharField(max_length=64, db_index=True)
    origin = models.CharField("origen", max_length=20, choices=Origin.choices)
    uploaded_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="+")
    author_name = models.CharField("autor", max_length=150)
    clinic = models.ForeignKey("clinics.Clinic", null=True, blank=True, on_delete=models.PROTECT, related_name="+")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "fotografía de la ficha"
        verbose_name_plural = "fotografías de la ficha"
        ordering = ["created_at"]

    def __str__(self) -> str:
        return f"{self.get_kind_display()} ({self.pet_id})"

    def save(self, *args, **kwargs):
        if not self._state.adding:
            raise ValueError("Las fotografías no se modifican.")
        super().save(*args, **kwargs)
