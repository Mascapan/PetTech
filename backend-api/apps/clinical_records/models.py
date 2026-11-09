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
